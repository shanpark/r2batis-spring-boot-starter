package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

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
    public void evaluateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, int orgArgCount, Map<String, Class<?>> placeholderMap, Map<String, Object> paramMap) {
        for (SqlNode sqlNode : sqlNodes)
            sqlNode.evaluateSql(paramInfos, args, orgArgCount, placeholderMap, paramMap);
    }

    @Override
    public String generateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, int orgArgCount, Map<String, Object> paramMap, Set<String> bindSet) {
        StringBuilder sb = new StringBuilder();
        for (SqlNode sqlNode : sqlNodes)
            sb.append(sqlNode.generateSql(paramInfos, args, orgArgCount, paramMap, bindSet)).append(" "); // 반드시 공백 붙여야 함.
        return sb.toString().trim(); // 마지막엔 항상 trim()
    }
}
