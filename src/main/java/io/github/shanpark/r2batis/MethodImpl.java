package io.github.shanpark.r2batis;

import io.github.shanpark.r2batis.sql.Insert;
import io.github.shanpark.r2batis.sql.Query;
import io.github.shanpark.r2batis.sql.Select;
import io.github.shanpark.r2batis.sql.SelectKey;
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
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Slf4j
public class MethodImpl {

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

    public MethodImpl(String name, Query query) {
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
    public Object invoke(DatabaseClient databaseClient, TransactionalOperator transactionalOperator, Method method, Object[] args) {
        int orgArgCount = args == null ? 0 : args.length;
        if (query instanceof Insert insert) {
            if (insert.getSelectKey() != null) {
                if (insert.getSelectKey().getOrder().equalsIgnoreCase("before")) {
                    return execSelectKeySql(databaseClient, insert.getSelectKey(), method, args, orgArgCount)
                            .then(Mono.defer(() -> (Mono<?>) execBodySql(databaseClient, method, args, orgArgCount)))
                            .as(transactionalOperator::transactional);
                } else if (insert.getSelectKey().getOrder().equalsIgnoreCase("after")) {
                    return ((Mono<?>) execBodySql(databaseClient, method, args, orgArgCount))
                            .flatMap(result ->
                                    execSelectKeySql(databaseClient, insert.getSelectKey(), method, args, orgArgCount).then(Mono.just(result))
                            )
                            .as(transactionalOperator::transactional);
                }
            }
        }

        return execBodySql(databaseClient, method, args, orgArgCount);
    }

    /**
     * &lt;selectKey&gt; 구문을 실행하는 Mono 생성.
     * &lt;selectKey&gt;의 SQL 문은 사실 자신이 속한 본문의 SQL과는 전혀 별개로 수행되어야 한다.
     * 단지 argument 정보를 공유할 뿐이며 실행 결과로 argument에 실행 결과 값이 반영될 것이다.
     *
     * @param databaseClient SQL을 실행할 DatabaseClient 객체.
     * @param selectKey &lt;selectKey&gt; Query 객체.
     * @param method Mapper 인터페이스의 Method 객체.
     * @param args Mapper 인터페이스의 메소드를 호출할 때 전달된 argument 들.
     * @return selectKey 구문이 반환하는 값을 발행하는 Mono 객체.
     */
    private Mono<?> execSelectKeySql(DatabaseClient databaseClient, SelectKey selectKey, Method method, Object[] args, int orgArgCount) {
        Map<String, Class<?>> placeholderMap = new HashMap<>();
        Map<String, Object> paramMap = new HashMap<>();
        Set<String> bindSet = new HashSet<>();

        ParamInfo[] paramInfos = getParamInfos(method.getParameters());

        String sql = selectKey.generateSql(paramInfos, args, orgArgCount, placeholderMap, paramMap, bindSet);
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        try {
            for (String placeholder : bindSet) {
                Object param = Ognl.getValue(placeholder, paramMap);
                if (param == null)
                    spec = spec.bindNull(placeholder, placeholderMap.get(placeholder));
                else
                    spec = spec.bind(placeholder, TypeUtils.convertForParam(param));
            }
        } catch (OgnlException e) {
            throw new RuntimeException(e);
        }

        return spec.fetch()
                .one()
                .doOnNext(resultMap -> {
                    Object selectedValue;
                    if (!selectKey.getKeyColumn().isBlank()) {
                        selectedValue = resultMap.get(selectKey.getKeyColumn());
                        if (selectedValue == null)
                            throw new RuntimeException("The 'keyColumn' attribute used in the <selectKey> element is not valid.");
                    } else {
                        selectedValue = resultMap.values().iterator().next();
                    }
                    selectedValue = TypeUtils.convert(selectedValue, selectKey.getResultClass());

                    try {
                        String[] fields = selectKey.getKeyProperty().split("\\.");
                        if (fields.length == 1) {
                            Ognl.setValue(selectKey.getKeyProperty().trim(), args[0], selectedValue);
                        } else {
                            Ognl.setValue(selectKey.getKeyProperty().substring(selectKey.getKeyProperty().indexOf('.') + 1), // 맨 앞의 "field." 부분은 떼 내야 한다.
                                    ReflectionUtils.findArgument(fields[0], paramInfos, args, orgArgCount), selectedValue);
                        }
                    } catch (OgnlException e) {
                        throw new RuntimeException("The 'keyProperty' expression used in the <selectKey> element is not valid.", e);
                    }
                });
    }

    public Publisher<?> execBodySql(DatabaseClient databaseClient, Method method, Object[] args, int orgArgCount) {
        Map<String, Class<?>> placeholderMap = new HashMap<>();
        Map<String, Object> paramMap = new HashMap<>();
        Set<String> bindSet = new HashSet<>();

        ParamInfo[] paramInfos = getParamInfos(method.getParameters());

        String sql = query.generateSql(paramInfos, args, orgArgCount, placeholderMap, paramMap, bindSet);
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        try {
            for (String placeholder : bindSet) {
                Object param = Ognl.getValue(placeholder, paramMap);
                if (param == null)
                    spec = spec.bindNull(placeholder, placeholderMap.get(placeholder));
                else
                    spec = spec.bind(placeholder, TypeUtils.convertForParam(param));
            }
        } catch (OgnlException e) {
            throw new RuntimeException(e);
        }

        try {
            if (query instanceof Insert insert) { // insert
                if (insert.isGenerateKeys()) {
                    // 결과가 생성된 PK가 나오도록 하는 filter를 적용한다.
                    //   Multi Insert의 경우 MariaDB는 10.5.1 부터 적용된다고 한다. MySQL은 맨 처음 생성된 ID 하나만 반환된다.
                    //   따라서 여러 건을 insert하는 경우 generated pk 값을 가져오는 건 현재 안된다고 봐야한다.
                    spec = spec.filter(s -> s.returnGeneratedValues(((Insert) query).getKeyProperty()));
                    return fetchByReturnType(spec, method, insert);
                } else {
                    return fetchRowsUpdated(spec, query);
                }
            } else if (query instanceof Select) { // select
                return fetchByReturnType(spec, method, query);
            } else { // update, delete
                return fetchRowsUpdated(spec, query);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
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
    private Publisher<?> fetchByReturnType(DatabaseClient.GenericExecuteSpec spec, Method method, Query query) throws ClassNotFoundException {
        if (Flux.class.isAssignableFrom(method.getReturnType())) {
            return spec.fetch()
                    .all()
                    .map(map -> ReflectionUtils.newInstanceFromMap(map, query.getResultClass()));
        } else {
            return spec.fetch()
                    .one()
                    .map(map -> ReflectionUtils.newInstanceFromMap(map, query.getResultClass()));
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
        if ((query.getResultClass() != null) && !query.getResultClass().equals(Long.class)) // rowsUpdated()는 Mono<Long> 반환
            return spec.fetch()
                    .rowsUpdated()
                    .map(count -> TypeUtils.convert(count, query.getResultClass()));
        else
            return spec.fetch()
                    .rowsUpdated();
    }
}
