package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import ognl.Ognl;
import ognl.OgnlException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

public final class If extends SqlNode {

    private final String test;

    private final List<SqlNode> sqlNodes = new ArrayList<>();

    public If(Element element) {
        test = element.getAttribute("test");

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
        try {
            if ((Boolean) Ognl.getValue(test, mapperContext.getParamMap())) { // test 조건 검사. 현 시점 이전에 생성된 모든 로컬 변수까지도 반영되어야 하므로 getParamMap()을 사용하는 게 맞다.
                StringBuilder sb = new StringBuilder();
                for (SqlNode sqlNode : sqlNodes)
                    sb.append(sqlNode.generateSql(mapperContext)).append(" "); // 하위 노드가 생성한 sql뒤에 항상 공백을 붙인다.
                return sb.toString().trim(); // 마지막엔 항상 trim()
            } else {
                return "";
            }
        } catch (OgnlException e) {
            throw new InvalidMapperElementException("The 'test' expression provided is invalid.", e);
        }
    }
}
