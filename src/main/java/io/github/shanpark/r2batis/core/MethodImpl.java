package io.github.shanpark.r2batis.core;

import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import io.github.shanpark.r2batis.mapper.*;
import io.github.shanpark.r2batis.util.ReflectionUtils;
import io.github.shanpark.r2batis.util.TypeUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ognl.Ognl;
import ognl.OgnlException;
import org.reactivestreams.Publisher;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class MethodImpl {

    private final InterfaceImpl host;
    private final String name;
    private final Query query;

    private volatile ParamInfo[] innerParams; // 캐슁 대상.

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParamInfo {
        String name;
        Class<?> type;
    }

    public MethodImpl(InterfaceImpl host, String name, Query query) {
        this.host = host;
        this.name = name;
        this.query = query;
    }

    /**
     * Mapper 인터페이스의 메소드를 호출한다. 이 메소드는 XML 맵퍼에 설정된 SQL문을 실행한다.
     *
     * @param databaseClient SQL을 실행할 DatabaseClient 객체.
     * @param method Mapper 인터페이스의 Method 객체.
     * @param args Mapper 인터페이스의 메소드를 호출할 때 전달된 argument 들.
     * @return Mapper 인터페이스가 반환해야 하는 값.
     */
    public Object invoke(DatabaseClient databaseClient, Method method, Object[] args) {
        List<SelectKey> selectKeys;
        if ((query instanceof Insert insert) && !insert.getSelectKeys().isEmpty())
            selectKeys = insert.getSelectKeys();
        else if ((query instanceof Update update) && !update.getSelectKeys().isEmpty())
            selectKeys = update.getSelectKeys();
        else
            selectKeys = null;

        if (selectKeys != null) {
            Mono<?> beforeMono = Mono.empty();
            for (SelectKey selectKey : selectKeys) {
                if (selectKey.getOrder().equalsIgnoreCase("before"))
                    beforeMono = beforeMono.then(Mono.defer(() -> execSelectKeySql(databaseClient, selectKey, method, args)));
            }
            return beforeMono.then(Mono.defer(() -> { // main sql의 생성(execBodySql()의 호출)은 before mono의 생성뿐만 아니라 실행이 완료될 때 까지 지연되어야 한다. 그래서 defer() 사용.
                        return ((Mono<?>) execBodySql(databaseClient, method, args))
                                .flatMap(result ->
                                        Mono.defer(() -> { // after mono의 생성도 execBodySql()이 반환한 모노가 실행이 완료될 때 까지 지연되어야 한다. 여기서도 defer()를 사용해야 맞다.
                                            Mono<?> afterMono = Mono.empty();
                                            for (SelectKey selectKey : selectKeys) {
                                                if (selectKey.getOrder().equalsIgnoreCase("after"))
                                                    afterMono = afterMono.then(Mono.defer(() -> execSelectKeySql(databaseClient, selectKey, method, args)));
                                            }
                                            return afterMono.then(Mono.just(result));
                                        })
                                );
                    }));
        } else {
            return execBodySql(databaseClient, method, args);
        }
    }

    /**
     * {@code <selectKey>} 구문을 실행하는 Mono 생성.
     * {@code <selectKey>}의 SQL 문은 사실 자신이 속한 본문의 SQL과는 전혀 별개로 수행되어야 한다.
     * 단지 argument 정보를 공유할 뿐이며 실행 결과로 argument에 실행 결과 값이 반영될 것이다.
     * TODO 따라서 항상 값을 반환하는 select 문 이어야 한다. 그렇지 않으면 에러 발생해야 한다.
     *
     * @param databaseClient SQL을 실행할 DatabaseClient 객체.
     * @param selectKey {@code <selectKey>} Query 객체.
     * @param method Mapper 인터페이스의 Method 객체.
     * @param args Mapper 인터페이스의 메소드를 호출할 때 전달된 argument 들.
     * @return selectKey 구문이 반환하는 값을 발행하는 Mono 객체.
     */
    private Mono<?> execSelectKeySql(DatabaseClient databaseClient, SelectKey selectKey, Method method, Object[] args) {
        MapperContext mapperContext = MapperContext.of(getParamInfos(method.getParameters()), args);

        String sql = selectKey.generateSql(mapperContext);
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        try {
            Map<String, Object> paramMap = mapperContext.getParamMap();
            for (String placeholder : mapperContext.getBindSet()) {
                Object param = Ognl.getValue(placeholder, paramMap);
                if (param == null)
                    spec = spec.bindNull(placeholder, mapperContext.getPlaceholderType(placeholder));
                else
                    spec = spec.bind(placeholder, TypeUtils.convertForParam(param));
            }
        } catch (OgnlException e) {
            throw new InvalidMapperElementException(e);
        }

        return spec.fetch()
                .one()
                .doOnNext(resultMap -> {
                    Object selectedValue;
                    if (!selectKey.getKeyColumn().isBlank()) {
                        selectedValue = resultMap.get(selectKey.getKeyColumn());
                        if (selectedValue == null)
                            throw new InvalidMapperElementException("The 'keyColumn' attribute used in the <selectKey> element is not valid.");
                    } else {
                        selectedValue = resultMap.values().iterator().next();
                    }
                    selectedValue = TypeUtils.convert(selectedValue, selectKey.getResultClass());

                    try {
                        String[] fields = selectKey.getKeyProperty().split("\\s*\\.\\s*");
                        Class<?> targetType = mapperContext.getTypeByFullFields(fields);
                        if (fields.length == 1) {
                            // keyProperty는 반드시 method arg로 넘겨진 pojo 객체의 한 필드이어야 한다. 따라서 field 가 1개라면 method arg도 1개이어야 그 arg의 필드로 판단해서 값을 설정할 수 있다.
                            if (mapperContext.getMethodArgs().size() == 1)
                                Ognl.setValue(selectKey.getKeyProperty().trim(), args[0], TypeUtils.convert(selectedValue, targetType));
                            else
                                throw new InvalidMapperElementException("The 'keyProperty' expression cannot be resolved.");
                        } else if (fields.length > 1) {
                            // field 가 여러 개로 이루어졌다면 method arg 중에 하나가 pojo가 될 것이고 해당 arg를 찾아서 그 arg의 field에 값을 설정해야 한다.
                            Ognl.setValue(selectKey.getKeyProperty().substring(selectKey.getKeyProperty().indexOf('.') + 1),
                                    mapperContext.getVarByField0(fields[0]),
                                    TypeUtils.convert(selectedValue, targetType));
                        } else {
                            throw new InvalidMapperElementException("The 'keyProperty' expression cannot be resolved.");
                        }
                    } catch (OgnlException e) {
                        throw new InvalidMapperElementException("The 'keyProperty' expression used in the <selectKey> element is not valid.", e);
                    }
                });
    }

    public Publisher<?> execBodySql(DatabaseClient databaseClient, Method method, Object[] args) {
        MapperContext mapperContext = MapperContext.of(getParamInfos(method.getParameters()), args);

        String sql = query.generateSql(mapperContext);
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        try {
            Map<String, Object> paramMap = mapperContext.getParamMap();
            for (String placeholder : mapperContext.getBindSet()) {
                Object param = Ognl.getValue(placeholder, paramMap);
                if (param == null)
                    spec = spec.bindNull(placeholder, mapperContext.getPlaceholderType(placeholder));
                else
                    spec = spec.bind(placeholder, TypeUtils.convertForParam(param));
            }
        } catch (OgnlException e) {
            throw new InvalidMapperElementException(e);
        }

        // useGeneratedKeys 속성은 multi insert, update를 하는 경우 반환값을 받기 위함이다.
        // 하지만 현재는 제대로 지원되지 않고 있다.
        // - MySql의 경우 23년 현재 하나만 받을 수 있다
        // - MariaDB의 경우에도 10.5.1 이전 버전은 1개만 받을 수 있다. 이 후 버전은 테스트 하지 못한 상태이다.
        // 따라서 여러 건을 insert, update 하는 경우 generated pk 값을 가져오는 건 현재 안된다고 봐야한다.
        if (query instanceof Insert insert) { // insert
            if (insert.isGenerateKeys()) {
                spec = spec.filter(s -> s.returnGeneratedValues(insert.getKeyColumn())); // 결과로 생성된 값이 나오도록 하는 filter를 적용한다.
                return fetchByReturnType(spec, method, query); // useGeneratedKeys가 지정되면 생성된 키값이 반환된다. updatedRows() 값은 포기해야 한다. R2DBC는 둘 중 하나만 선택가능하다.
            } else {
                return fetchRowsUpdated(spec, query);
            }
        } else if (query instanceof Update update) { // update
            if (update.isGenerateKeys()) {
                spec = spec.filter(s -> s.returnGeneratedValues(update.getKeyColumn())); // 결과로 생성된 값이 나오도록 하는 filter를 적용한다.
                return fetchByReturnType(spec, method, query); // useGeneratedKeys가 지정되면 생성된 키값이 반환된다. updatedRows() 값은 포기해야 한다. R2DBC는 둘 중 하나만 선택가능하다.
            } else {
                return fetchRowsUpdated(spec, query);
            }
        } else if (query instanceof Select) { // select
            return fetchByReturnType(spec, method, query);
        } else { // delete
            return fetchRowsUpdated(spec, query);
        }
    }

    private ParamInfo[] getParamInfos(Parameter[] parameters) {
        if (innerParams == null) { // 한 번 생성하면 변동없으므로 캐슁한다.
            synchronized (this) {
                if (innerParams == null) {
                    innerParams = new ParamInfo[parameters.length];
                    for (int inx = 0; inx < parameters.length; inx++)
                        innerParams[inx] = new ParamInfo(parameters[inx].getName(), parameters[inx].getType());
                }
            }
        }
        return innerParams;
    }

    /**
     * Method의 반환값이 Mono냐 Flux냐에 따라서 수행 코드를 결정하여 SQL을 수행하도록 한다.
     *
     * @param spec DatabaseClient를 통해서 생성한 GenericExecuteSpec 객체
     * @param method 현재 호출된 Mapper 인터페이스의 Method 객체
     * @param query XML 맵퍼에서 생성된 SQL Query 객체.
     * @return SQL을 수행하고 값을 발행할 Publisher 객체. (Mono 또는 Flux)
     */
    private Publisher<?> fetchByReturnType(DatabaseClient.GenericExecuteSpec spec, Method method, Query query) {
        if (Flux.class.isAssignableFrom(method.getReturnType())) {
            return spec.fetch()
                    .all()
                    .map(map -> ReflectionUtils.newInstanceFromMap(map, query.getResultClass(), host.getR2batisProperties().isMapUnderscoreToCamelCase()));
        } else {
            return spec.fetch()
                    .one()
                    .map(map -> ReflectionUtils.newInstanceFromMap(map, query.getResultClass(), host.getR2batisProperties().isMapUnderscoreToCamelCase()));
        }
    }

    /**
     * 영향 받은 행의 갯수를 가져와서 지정된 타입으로 변환해서 반환한다.
     * 기본적으로 Integer 타입이 사용되기 때문에 따로 resultType이 지정되지 않았다면 Integer로 반환된다.
     * Long 같은 다른 숫자 타입으로 변환하길 원한다면 resultType을 지정해서 받는다.
     *
     * @param spec DatabaseClient를 통해서 생성한 GenericExecuteSpec 객체
     * @param query XML 맵퍼에서 생성된 SQL Query 객체.
     * @return SQL을 수행하고 영향 받은 행의 갯수 발행할 Publisher 객체.
     */
    private Mono<?> fetchRowsUpdated(DatabaseClient.GenericExecuteSpec spec, Query query) {
        if ((query.getResultClass() != null) && !query.getResultClass().equals(Long.class)) { // rowsUpdated()는 Mono<Long> 반환
            return spec.fetch()
                    .rowsUpdated()
                    .map(count -> TypeUtils.convert(count, query.getResultClass()));
        }
        else
            return spec.fetch()
                    .rowsUpdated();
    }
}
