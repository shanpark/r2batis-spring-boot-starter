package io.github.shanpark.r2batis.sql;

import lombok.Getter;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Getter
public class Insert extends Query {
    private final boolean useGeneratedKeys;
    private final String keyProperty;

    public Insert(Element element) {
        super(element);

        useGeneratedKeys = Boolean.parseBoolean(element.getAttribute("useGeneratedKeys"));
        keyProperty = element.getAttribute("keyProperty");

        if (isUseGeneratedKeys() && (getResultClass() == null))
            throw new RuntimeException("The <insert> element that uses generatedKeys should include the 'resultType' attribute.");

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

    public boolean isGenerateKeys() {
        return useGeneratedKeys && (keyProperty != null) && (super.getResultClass() != null);
    }
}
