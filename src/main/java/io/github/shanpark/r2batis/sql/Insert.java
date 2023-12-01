package io.github.shanpark.r2batis.sql;

import lombok.Getter;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Getter
public class Insert extends Query {
    private final boolean useGeneratedKeys;
    private final String keyProperty;
    private SelectKey selectKey; // TODO selectKey가 여러번 지정되면? BEFORE, AFTER 각각 지정되면?

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
                if (node.getNodeName().equals("selectKey")) { // <insert> 에는 <selectKey> 가 가능하다.
                    if (selectKey == null)
                        selectKey = new SelectKey((Element) node); // selectKey는 한 개만 가능.
                    else
                        throw new RuntimeException("The <selectKey> element should only be used once within an <insert> element.");
                } else {
                    sqlNodes.add(SqlNode.newSqlNode((Element) node));
                }
            }
        }
    }

    public boolean isGenerateKeys() {
        return useGeneratedKeys && (keyProperty != null) && (super.getResultClass() != null);
    }
}
