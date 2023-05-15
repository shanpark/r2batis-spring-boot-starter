package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import org.w3c.dom.Element;

import java.util.Map;
import java.util.Set;

public abstract class SqlNode {
    protected static long uid = 0;

    protected SqlNode() {
        uid++;
    }

    public static SqlNode newSqlNode(Element element) {
        String nodeName = element.getNodeName();
        return switch (nodeName) {
            case "if" -> new If(element);
            case "foreach" -> new Foreach(element);
            case "trim" -> new Trim(element);
            case "where" -> new Trim(element, "WHERE", "AND |OR ", ""); // AND, OR 뒤에 따라오는 공백문자 유지해야 함.
            case "set" -> new Trim(element, "SET", "", ",");
            case "choose" -> new Choose(element);
            default -> throw new RuntimeException("An invalid element was found. [" + nodeName + "]");
        };
    }

    public static SqlNode newSqlNodeForChoose(Element element) {
        String nodeName = element.getNodeName();
        return switch (nodeName) {
            case "when" -> new If(element);
            case "otherwise" -> new Otherwise(element);
            default -> throw new RuntimeException("An invalid element was found. [" + nodeName + "]");
        };
    }

    /**
     * placeholder와 그에 해당하는 value, type 을 뽑아서 map에 담아준다.
     *
     * @param paramInfos 인터페이스 메소드(Method)로 전달된 Parameter 배열.
     * @param args 인터페이스 호출 시 실제 전달된 argment의 배열.
     * @param placeholderMap 생성된 sql에서 뽑힌 placeholder와 그 타입을 담을 Map 객체.
     * @param paramMap 생성된 sql에서 뽑힌 placeholder와 실제 이 placeholder를 이용해서 Ognl로 값을 뽑아낼 수 있도록 생성할 Map 객체.
     */
    public abstract void evaluateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, Map<String, Class<?>> placeholderMap, Map<String, Object> paramMap);

    /**
     * 최종 생성된 SQL 문을 반환한다.
     * 항상 반횐된 SQL 문은 trim 상태이어야 한다.
     *
     * @param paramMap (placeholder, value) 를 entry로 갖는 Map 객체. Ognl로 placeholder를 이용해서 값을 가져올 수 있는 Map 객체이다.
     * @param bindSet 최종적으로 bind가 필요한 placeholder들을 담아서 반환한다. 생성되는 SQL에 따라서 binding이 필요 없게 되는
     *                placeholder들이 있는데 이런 placeholder에 bind()호출하면 Runtime에 UnsupportedOperationException 이 발생한다.
     * @return 최종 생성된 SQL문. trim 상태로 반환한다.
     */
    public abstract String generateSql(Map<String, Object> paramMap, Set<String> bindSet);
}
