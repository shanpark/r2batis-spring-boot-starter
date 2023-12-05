package io.github.shanpark.r2batis.sql;

import lombok.Getter;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Getter
public class Update extends Query {
    //!!! R2DBC의 Spec 객체가 updatedRows()와 one() 둘 중 하나만 실행이 가능하다.
    //    generatedKeys 값을 받으려면 one()을 실행해야 한다. 따라서 updatedRows 값은 포기해야 한다.
    //    결국 keyProperty를 이용한 반환은 안되고 메서드의 반환값으로만 받을 수 있도록 하였다.
    //    같은 이유로 useGeneratedKeys가 지정되면 returnType도 반드시 지정해야 한다. MyBatis와 달리 keyProperty는 소용없다.

    private final boolean useGeneratedKeys; // 생성된 키값을 직접 반환값으로 받는 것만 가능하다.
    private final String keyColumn; // 생성되는 키 컬럼을 지정한다. MyBatis와 다름.

    public Update(Element element) {
        super(element);

        useGeneratedKeys = Boolean.parseBoolean(element.getAttribute("useGeneratedKeys"));
        keyColumn = element.getAttribute("keyColumn");

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
        return useGeneratedKeys && (keyColumn != null) && (super.getResultClass() != null); // generated key가 실제 반환값이 되므로 항상 returnType이 지정되어야 한다.
    }
}
