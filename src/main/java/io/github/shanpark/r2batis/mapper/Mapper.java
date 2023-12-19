package io.github.shanpark.r2batis.mapper;

import lombok.Data;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class Mapper {

    private final String interfaceName;

    private final List<Select> selectList = new ArrayList<>();
    private final List<Insert> insertList = new ArrayList<>();
    private final List<Update> updateList = new ArrayList<>();
    private final List<Delete> deleteList = new ArrayList<>();

    /**
     * Mapper 클래스 생성자.
     *
     * @param databaseId DatabaseIdProvider가 반환한 현재 connection의 database ID. 구하지 못했다면 null일 수도 있다.
     * @param root 맵퍼 XML의 root element.
     */
    public Mapper(String databaseId, Element root) {
        interfaceName = root.getAttribute("namespace").trim();

        NodeList nodeList = root.getChildNodes();
        for (int inx = 0; inx < nodeList.getLength(); inx++) {
            Node node = nodeList.item(inx);

            if (node.getNodeType() == Node.ELEMENT_NODE) { // <mapper>는 자식으로 text는 모두 무시하고 <select>, <insert>, <update>, <delete> 만 인정.
                Element element = (Element) node;
                String nodeName = element.getNodeName();

                switch (nodeName) {
                    case "select" -> {
                        Select select = new Select(element);
                        if (select.getDatabaseId().isBlank() || Objects.equals(select.getDatabaseId(), databaseId))
                            selectList.add(select);
                    }
                    case "insert" -> {
                        Insert insert = new Insert(element);
                        if (insert.getDatabaseId().isBlank() || Objects.equals(insert.getDatabaseId(), databaseId))
                            insertList.add(insert);
                    }
                    case "update" -> {
                        Update update = new Update(element);
                        if (update.getDatabaseId().isBlank() || Objects.equals(update.getDatabaseId(), databaseId))
                            updateList.add(update);
                    }
                    case "delete" -> {
                        Delete delete = new Delete(element);
                        if (delete.getDatabaseId().isBlank() || Objects.equals(delete.getDatabaseId(), databaseId))
                            deleteList.add(delete);
                    }
                }
            }
        }
    }
}
