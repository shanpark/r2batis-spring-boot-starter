package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import ognl.Ognl;
import ognl.OgnlException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public void evaluateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, int orgArgCount, Map<String, Class<?>> placeholderMap, Map<String, Object> paramMap) {
        for (SqlNode sqlNode : sqlNodes)
            sqlNode.evaluateSql(paramInfos, args, orgArgCount, placeholderMap, paramMap);
    }

    @Override
    public String generateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, int orgArgCount, Map<String, Object> paramMap, Set<String> bindSet) {
        try {
            if ((Boolean) Ognl.getValue(test, paramMap)) { // test 조건 검사
                StringBuilder sb = new StringBuilder();
                for (SqlNode sqlNode : sqlNodes)
                    sb.append(sqlNode.generateSql(paramInfos, args, orgArgCount, paramMap, bindSet)).append(" "); // 반드시 공백 붙여야 함.
                return sb.toString().trim(); // 마지막엔 항상 trim()
            } else {
                return "";
            }
        } catch (OgnlException e) {
            throw new RuntimeException(e);
        }
    }
}
