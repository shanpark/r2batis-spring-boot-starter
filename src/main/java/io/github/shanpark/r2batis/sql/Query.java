package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
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
        id = element.getAttribute("id").trim();
        String resultType = element.getAttribute("resultType").trim();
        try {
            resultClass = !resultType.isBlank() ? Class.forName(resultType) : null;
        } catch (ClassNotFoundException e) {
            throw new InvalidMapperElementException(String.format("The specified resultType[%s] is invalid.", resultType), e);
        }
    }

    public String generateSql(MapperContext mapperContext) {
        StringBuilder sb = new StringBuilder();
        for (SqlNode sqlNode : sqlNodes)
            sb.append(sqlNode.generateSql(mapperContext)).append(" ");
        return sb.toString();
    }

    /**
     * 메소드로 전달된 argument들을 paramMap에 넣어서 초기화한다.
     * 기본적으로 메소드로 전달된 argument들은 이름 그대로 paramMap에 들어가 있어야 한다.
     *
     * @param paramMap (parameter 이름, argument) 를 entry로 갖는 Map 객체
     * @param paramInfos 메소드의 parameter 정보를 갖는 배열.
     * @param args 실제 메소드 호출 시에 전달된 arguments.
     */
//    static void initParamMap(Map<String, Object> paramMap, MethodImpl.ParamInfo[] paramInfos, Object[] args) {
//        for (int inx = 0; inx < paramInfos.length; inx++)
//            paramMap.put(paramInfos[inx].getName(), args[inx]);
//    }
}
