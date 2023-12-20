package io.github.shanpark.r2batis.mapper;

import lombok.Data;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

@Data
public class Mapper {

    private final String interfaceName;

    private final List<Select> selectList = new ArrayList<>(); // TODO List<Query> 하나로 합쳐도 될 것 같은데?
    private final List<Insert> insertList = new ArrayList<>();
    private final List<Update> updateList = new ArrayList<>();
    private final List<Delete> deleteList = new ArrayList<>();

    /**
     * Mapper 클래스 생성자.
     *
     * @param root 맵퍼 XML의 root element.
     */
    public Mapper(Element root) {
        interfaceName = root.getAttribute("namespace").trim();

        NodeList nodeList = root.getChildNodes();
        for (int inx = 0; inx < nodeList.getLength(); inx++) {
            Node node = nodeList.item(inx);

            if (node.getNodeType() == Node.ELEMENT_NODE) { // <mapper>는 자식으로 text는 모두 무시하고 <select>, <insert>, <update>, <delete> 만 인정.
                Element element = (Element) node;
                String nodeName = element.getNodeName();

                switch (nodeName) {
                    case "select" -> selectList.add(new Select(element));
                    case "insert" -> insertList.add(new Insert(element));
                    case "update" -> updateList.add(new Update(element));
                    case "delete" -> deleteList.add(new Delete(element));
                }
            }
        }
    }
}
