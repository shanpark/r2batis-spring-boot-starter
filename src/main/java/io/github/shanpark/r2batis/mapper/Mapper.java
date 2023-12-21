package io.github.shanpark.r2batis.mapper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
public class Mapper {

    private final String interfaceName;
    private final List<Query> queryList = new ArrayList<>();

    public Mapper() {
        interfaceName = ""; // 이렇게 생성된 Mapper는 empty mapper 역할일 뿐 mapper 처리하는 루틴에서 아무 일도 하지 않도록 한다.
    }

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
                    case "select" -> queryList.add(new Select(element));
                    case "insert" -> queryList.add(new Insert(element));
                    case "update" -> queryList.add(new Update(element));
                    case "delete" -> queryList.add(new Delete(element));
                    default -> log.warn("Invalid mapper element(<{}>) was found.", nodeName);
                }
            }
        }
    }
}
