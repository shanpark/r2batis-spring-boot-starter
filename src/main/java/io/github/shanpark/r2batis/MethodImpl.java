package io.github.shanpark.r2batis;

import io.github.shanpark.r2batis.sql.Insert;
import io.github.shanpark.r2batis.sql.Query;
import io.github.shanpark.r2batis.sql.Select;
import io.github.shanpark.r2batis.util.ReflectionUtils;
import io.github.shanpark.r2batis.util.TypeUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ognl.Ognl;
import ognl.OgnlException;
import org.springframework.r2dbc.core.DatabaseClient;
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
@RequiredArgsConstructor
public class MethodImpl {

    private String name;
    private Query query;

    private ParamInfo[] innerParams;

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
        innerParams = null;
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
        Map<String, Class<?>> placeholderMap = new HashMap<>();
        Map<String, Object> paramMap = new HashMap<>();
        Set<String> bindSet = new HashSet<>();

        ParamInfo[] paramInfos = getParamInfos(method.getParameters());

        String sql = query.generateSql(paramInfos, args, placeholderMap, paramMap, bindSet);
        log.trace("Generated SQL: {}", sql);
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
                    return fetchRowsUpdated(spec, method, query);
                }
            } else if (query instanceof Select) { // select
                return fetchByReturnType(spec, method, query);
            } else { // update, delete
                return fetchRowsUpdated(spec, method, query);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private ParamInfo[] getParamInfos(Parameter[] parameters) {
        if (innerParams == null) {
            innerParams = new ParamInfo[parameters.length];
            for (int inx = 0; inx < parameters.length; inx++)
                innerParams[inx] = new ParamInfo(parameters[inx].getName(), parameters[inx].getType());
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
    private Object fetchByReturnType(DatabaseClient.GenericExecuteSpec spec, Method method, Query query) throws ClassNotFoundException {
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
     * @param method 현재 호출된 Mapper 인터페이스의 Method 객체
     * @param query XML 맵퍼에서 생성된 SQL Query 객체.
     * @return SQL을 수행하고 영향 받은 행의 갯수 발행할 Publisher 객체.
     */
    private Object fetchRowsUpdated(DatabaseClient.GenericExecuteSpec spec, Method method, Query query) {
        Mono<?> mono;
        if ((query.getResultClass() != null) && !query.getResultClass().equals(Integer.class))
            mono = spec.fetch().rowsUpdated()
                    .map(count -> TypeUtils.convert(count, query.getResultClass()));
        else
            mono = spec.fetch().rowsUpdated();

        return Flux.class.isAssignableFrom(method.getReturnType()) ? mono.flux() : mono;
    }
}
