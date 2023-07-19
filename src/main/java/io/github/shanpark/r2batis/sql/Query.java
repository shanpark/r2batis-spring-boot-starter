package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import lombok.Getter;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
public class Query {

    private final String id;
    private final Class<?> resultClass;

    protected final List<SqlNode> sqlNodes = new ArrayList<>();

    public Query(Element element) {
        id = element.getAttribute("id");
        String resultType = element.getAttribute("resultType");
        try {
            resultClass = !resultType.isBlank() ? Class.forName(resultType) : null;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(String.format("The specified resultType[%s] is invalid.", resultType), e);
        }
    }

    public String generateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, int orgArgCount, Map<String, Class<?>> placeholderMap, Map<String, Object> paramMap, Set<String> bindSet) {
        initParamMap(paramMap, paramInfos, args); // test 조건 같은 곳에서 사용되는 것들이 placeholder로 검출되지 않기 떄문에 최초에 기본적으로 모든 argument는 paramMap에 자기 이름을 키값으로 들어가야 맞다.

        for (SqlNode sqlNode : sqlNodes)
            sqlNode.evaluateSql(paramInfos, args, orgArgCount, placeholderMap, paramMap);

        StringBuilder sb = new StringBuilder();
        for (SqlNode sqlNode : sqlNodes)
            sb.append(sqlNode.generateSql(paramMap, bindSet)).append(" ");
        return sb.toString();
    }

    /**
     * 메소드로 전달된 arguments들을 paramMap에 넣어서 초기화한다.
     *
     * @param paramMap (parameter 이름, argument) 를 entry로 갖는 Map 객체
     * @param paramInfos 메소드의 parameter 정보를 갖는 배열.
     * @param args 실제 메소드 호출 시에 전달된 arguments.
     */
    static void initParamMap(Map<String, Object> paramMap, MethodImpl.ParamInfo[] paramInfos, Object[] args) {
        for (int inx = 0; inx < paramInfos.length; inx++)
            paramMap.put(paramInfos[inx].getName(), args[inx]);
    }
}
