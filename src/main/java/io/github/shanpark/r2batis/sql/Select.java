package io.github.shanpark.r2batis.sql;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Select extends Query {
    public Select(Element element) {
        super(element);

        if (getResultClass() == null)
            throw new RuntimeException("The <select> element should include the 'resultType' attribute.");

        NodeList nodeList = element.getChildNodes();
        for (int inx = 0; inx < nodeList.getLength(); inx++) {
            Node node = nodeList.item(inx);

            if (node.getNodeType() == Node.TEXT_NODE) {
                String content = node.getNodeValue();
                if (!content.isBlank())
                    sqlNodes.add(new Sql(content.trim()));
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                sqlNodes.add(SqlNode.newSqlNode((Element) node));
            }
        }
    }
}
