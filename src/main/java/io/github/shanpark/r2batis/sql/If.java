package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import ognl.Ognl;
import ognl.OgnlException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

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
        // TODO 여기서 이미 test를 수행하면 아래 모두 평가하지 않아도 되는데?? 그럴려면 평가된 값을 parameter로 전달해야 한다.
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
            throw new InvalidMapperElementException("The 'test' expression provided is invalid.", e);
        }
    }
}
