package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Trim extends SqlNode {

    private final String prefix;
    private final Pattern prefixPattern;
    private final Pattern suffixPattern;

    private final List<SqlNode> sqlNodes = new ArrayList<>();

    public Trim(Element element) {
        // prefixOverrides 속성은 (?i)^(~~~) 안에 들어갈 정규식 형태 문자열이다.
        // suffixOverrides 속성은 (?i)(~~~)$ 안에 들어갈 정규식 형태 문자열이다.
        this(element, element.getAttribute("prefix"), element.getAttribute("prefixOverrides"), element.getAttribute("suffixOverrides"));
    }

    /**
     * @param element trim 노드를 나타내는 element 객체.
     * @param prefix 생성된 SQL의 맨 앞에 붙여줄 문자열. 생성된 문자열이 있다면 prefix 뒤에 항상 공백이 하나 더 붙는다.
     * @param prefixOverrides 생성된 SQL의 시작 부분에서 삭제할 문자열. 정규식 "(?i)^(~~~)" 에서 ~~~ 부분에 들어갈 정규식이어야 한다.
     * @param suffixOverrides 생성된 SQL의 끝 부분에서 삭제할 문자열. 정규식 "(?i)(~~~)$" 에서 ~~~ 부분에 들어갈 정규식이어야 한다.
     */
    public Trim(Element element, String prefix, String prefixOverrides, String suffixOverrides) {
        this.prefix = prefix.trim();

        if (prefixOverrides.isEmpty() && suffixOverrides.isEmpty())
            throw new RuntimeException("'prefixOverrides' or 'suffixOverrides' attribute should be specified for <trim>.");

        prefixPattern = prefixOverrides.isBlank() ? null : Pattern.compile("(?i)^(" + prefixOverrides + ")");
        suffixPattern = suffixOverrides.isBlank() ? null : Pattern.compile("(?i)(" + suffixOverrides + ")$");

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

        String sql = sb.toString().trim();
        if (sql.isBlank()) {
            return prefix; // 아무런 sql이 생성되지 않았더라도 prefix는 생성해야 한다. // TODO MyBatis의 <where>의 동작을 보면 아닌 듯하다. 아무것도 없으면 prefix도 없어야 맞는듯..
        } else {
            // 시작에 AND 또는 OR 가 있으면 삭제.
            if (prefixPattern != null) {
                Matcher matcher = prefixPattern.matcher(sql);
                if (matcher.find())
                    sql = sql.substring(matcher.end()).trim();
            }
            if (suffixPattern != null) {
                // 마지막에 AND 또는 OR 가 있으면 삭제.
                Matcher matcher = suffixPattern.matcher(sql);
                if (matcher.find())
                    sql = sql.substring(0, matcher.start()).trim();
            }

            return prefix + " " + sql; // 이미 trim 되어있다.
        }
    }
}
