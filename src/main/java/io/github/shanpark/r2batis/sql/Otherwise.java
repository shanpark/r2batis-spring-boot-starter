package io.github.shanpark.r2batis.sql;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * HTML에서 <div> 처럼 SQL 구문들을 그저 block으로 묶어주는 역할말고는 없다.
 */
public final class Otherwise extends SqlNode {

    private final List<SqlNode> sqlNodes = new ArrayList<>();

    public Otherwise(Element element) {
        NodeList nodeList = element.getChildNodes();
        for (int inx = 0; inx < nodeList.getLength(); inx++) {
            Node node = nodeList.item(inx);

            if (node.getNodeType() == Node.TEXT_NODE) {
                String content = node.getNodeValue();
                if (!content.isBlank())
                    sqlNodes.add(new Sql(content.trim()));
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                sqlNodes.add(newSqlNode((Element) node));
            }
        }
    }

    @Override
    public String generateSql(MapperContext mapperContext) {
        StringBuilder sb = new StringBuilder();
        for (SqlNode sqlNode : sqlNodes)
            sb.append(sqlNode.generateSql(mapperContext)).append(" "); // 하위 노드가 생성한 sql뒤에 항상 공백을 붙인다.
        return sb.toString().trim(); // 마지막엔 항상 trim()
    }
}
