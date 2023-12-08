package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import org.w3c.dom.Element;

import java.util.concurrent.atomic.AtomicLong;

public abstract class SqlNode {
    protected static final AtomicLong uid = new AtomicLong(0L);

    public static SqlNode newSqlNode(Element element) {
        String nodeName = element.getNodeName();
        return switch (nodeName) {
            case "if" -> new If(element);
            case "foreach" -> new Foreach(element);
            case "trim" -> new Trim(element);
            case "where" -> new Trim(element, "WHERE", "AND |OR ", ""); // AND, OR 뒤에 따라오는 공백문자 유지해야 함.
            case "set" -> new Trim(element, "SET", "", ",");
            case "choose" -> new Choose(element);
            default -> throw new InvalidMapperElementException("An invalid element was found. [" + nodeName + "]");
        };
    }

    public static SqlNode newSqlNodeForChoose(Element element) {
        String nodeName = element.getNodeName();
        return switch (nodeName) {
            case "when" -> new If(element);
            case "otherwise" -> new Otherwise(element);
            default -> throw new InvalidMapperElementException("An invalid element was found. [" + nodeName + "]");
        };
    }

    /**
     * 최종 생성된 SQL 문을 반환한다.
     * 항상 반횐된 SQL 문은 trim 상태이어야 한다.
     *
     * @param mapperContext SQL 생성 작업의 진행 상태가 저장된다.
     * @return 최종 생성된 SQL문. trim 상태로 반환한다.
     */
    public abstract String generateSql(MapperContext mapperContext);
}
