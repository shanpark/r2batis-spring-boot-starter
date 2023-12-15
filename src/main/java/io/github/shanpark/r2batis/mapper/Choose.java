package io.github.shanpark.r2batis.mapper;

import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

public final class Choose extends SqlNode {

    private final List<If> whenNodes = new ArrayList<>(); // <when>은 <if> 모양, 기능이 같다. 단지 <choose> 내에서만 의미가 있을 뿐이다.
    private Otherwise otherwise;

    public Choose(Element element) {
        NodeList nodeList = element.getChildNodes();
        for (int inx = 0; inx < nodeList.getLength(); inx++) {
            Node node = nodeList.item(inx);

            if (node.getNodeType() == Node.TEXT_NODE) {
                String content = node.getNodeValue();
                if (!content.isBlank())
                    throw new InvalidMapperElementException("The <choose> element can only contain <when> or <otherwise> elements.");
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                SqlNode child = SqlNode.newSqlNodeForChoose((Element) node);
                if (child instanceof If) {
                    whenNodes.add((If) child);
                } else {
                    if (otherwise == null)
                        otherwise = (Otherwise) child;
                    else
                        throw new InvalidMapperElementException("The <otherwise> element has been specified multiple times.");
                }
            }
        }
        if (whenNodes.isEmpty())
            throw new InvalidMapperElementException("The <choose> element should have at least one <when> element.");
    }

    @Override
    public String generateSql(MapperContext mapperContext) {
        for (If sqlNode : whenNodes) {
            String sql = sqlNode.generateSql(mapperContext);
            if (!sql.isBlank()) // 생성된 sql이 있으면 바로 반환하고 더 이상 처리를 할 필요가 없다.
                return sql.trim();
        }

        if (otherwise != null)
            return otherwise.generateSql(mapperContext);

        return "";
    }
}
