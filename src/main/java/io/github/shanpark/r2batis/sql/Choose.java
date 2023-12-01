package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Choose extends SqlNode {

    private final List<If> whenNodes = new ArrayList<>(); // <when>은 <if> 모양, 기능이 같다. 단지 <choose> 내에서만 의미가 있을 뿐이다.
    private Otherwise otherwise;

    public Choose(Element element) {
        NodeList nodeList = element.getChildNodes();
        for (int inx = 0; inx < nodeList.getLength(); inx++) {
            Node node = nodeList.item(inx);

            if (node.getNodeType() == Node.TEXT_NODE) {
                String content = node.getNodeValue();
                if (!content.isBlank())
                    throw new RuntimeException("The <choose> element can only contain <when> or <otherwise> elements.");
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                SqlNode child = SqlNode.newSqlNodeForChoose((Element) node);
                if (child instanceof If) {
                    whenNodes.add((If) child);
                } else {
                    if (otherwise == null)
                        otherwise = (Otherwise) child;
                    else
                        throw new RuntimeException("The <otherwise> element was specified twice.");
                }
            }
        }
        if (whenNodes.isEmpty())
            throw new RuntimeException("The <choose> element should have at least one <when> element.");
    }

    @Override
    public void evaluateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, int orgArgCount, Map<String, Class<?>> placeholderMap, Map<String, Object> paramMap) {
        for (SqlNode sqlNode : whenNodes)
            sqlNode.evaluateSql(paramInfos, args, orgArgCount, placeholderMap, paramMap);

        if (otherwise != null)
            otherwise.evaluateSql(paramInfos, args, orgArgCount, placeholderMap, paramMap);
    }

    @Override
    public String generateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, int orgArgCount, Map<String, Object> paramMap, Set<String> bindSet) {
        for (If sqlNode : whenNodes) {
            String sql = sqlNode.generateSql(paramInfos, args, orgArgCount, paramMap, bindSet);
            if (!sql.isBlank())
                return sql.trim();
        }

        if (otherwise != null)
            return otherwise.generateSql(paramInfos, args, orgArgCount, paramMap, bindSet);

        return "";
    }
}
