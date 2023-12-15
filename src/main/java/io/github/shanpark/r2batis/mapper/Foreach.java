package io.github.shanpark.r2batis.mapper;

import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import lombok.Getter;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public final class Foreach extends SqlNode {

    // 참고로 MyBatis는 nullable 속성이 하나 더 있다. 하지만 이건 nullable은 true이다.
    private final String collection; // required
    private final String item;
    private final String index;
    private final String open;
    private final String close;
    private final String separator;

    private final String uniqueId;
    private final List<SqlNode> sqlNodes = new ArrayList<>();

    public Foreach(Element element) {
        collection = element.getAttribute("collection").trim();
        if (collection.isBlank())
            throw new InvalidMapperElementException("The <foreach> element must include the 'collection' attribute.");

        item = element.getAttribute("item").trim();
        index = element.getAttribute("index").trim();
        open = element.getAttribute("open");
        close = element.getAttribute("close");
        separator = element.getAttribute("separator");
        uniqueId = String.valueOf(uid.incrementAndGet());

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
    public String generateSql(MapperContext mapperContext) {
        // collection에 지정된 건 argument.field 일 수도 있으므로 Ognl이 적용되어야 한다.
        Collection<?> collectionParam = (Collection<?>) mapperContext.getVarByFullFields(collection); // 가져온 값은 반드시 Collection 이어야 한다. 아니면 ClassCaseException이 발생하겠지.

        if (!collectionParam.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            StringBuilder tempSb = new StringBuilder();

            int inx = 0;
            for (Object collectionItem : collectionParam) {
                mapperContext.newBranch("_" + inx);

                // index, item으로 지정된 값들을 local var 로 추가해준다.
                if (!index.isBlank())
                    mapperContext.pushLocalVar(index, Integer.class, inx); // index 이름으로 local var를 하나 추가한다.
                if (!item.isBlank())
                    mapperContext.pushLocalVar(item, collectionItem.getClass(), collectionItem); // item 이름으로 local var를 하나 추가한다.

                // 실제 SQL 생성.
                for (SqlNode sqlNode : sqlNodes)
                    tempSb.append(sqlNode.generateSql(mapperContext)); // generateSql()은 trim을 해서 보내므로 따로 trim()은 필요없다.

                // 생성된 SQL 에서 item, index 참조하는 부분도 새 이름으로 바꿔줘야 한다.
                if (!tempSb.isEmpty()) {
                    tempSb.append(' '); // SQL의 맨 마지막에 있는 :item, :index 에 매칭하려면 맨 뒤에 공백이 하나 붙어야 한다.
                    String sql = tempSb.toString();
                    if (!item.isBlank()) {
                        mapperContext.expandPlaceholder(item, getNewItemName(mapperContext));
                        sql = expandLocalPlaceholder(sql, item, getNewItemName(mapperContext));
                        mapperContext.popLocalVar(); // item 로컬 변수 제거. 이름이 없어도 push 의 역순으로 호출되면 문제 없다.
                    }
                    if (!index.isBlank()) {
                        mapperContext.expandPlaceholder(index, getNewIndexName(mapperContext));
                        sql = expandLocalPlaceholder(sql, index, getNewIndexName(mapperContext));
                        mapperContext.popLocalVar(); // index 로컬 변수 제거. 이름이 없어도 push 의 역순으로 호출되면 문제 없다.
                    }

                    if (!sb.isEmpty())
                        sb.append(separator);
                    sb.append(sql.trim());
                }

                tempSb.setLength(0);

                mapperContext.mergeBranch();
                inx++;
            }

            return ((open.isBlank() ? "" : open + " ") +
                    sb +
                    (close.isBlank() ? "" : " " + close)).trim();
        } else {
            return "";
        }
    }

    private String getNewItemName(MapperContext mapperContext) {
        // 중첩되지 않은 2개 이상의 foreach와 중첩된 foreach를 모두 고려해서 전체 sql 내에서 유일한 식별자 값이 되려면
        // foreach의 uniqueId, branch의 uniqueId 모두 들어가야 전체 sql 내에서 유일하게 된다.
        return item + "_" + uniqueId + mapperContext.getUniqueId();
    }

    private String getNewIndexName(MapperContext mapperContext) {
        return index + "_" + uniqueId + mapperContext.getUniqueId();
    }

    /**
     * foreach 요소에서 생성되는 로컬 변수인 item, index를 사용한 placeholder들을 중첩되어도 문제가 없도록
     * 새로운 이름으로 교체해준다.
     *
     * @param generated 로컬 변수이름을 그대로 사용해서 생성된 SQL
     * @param placeholder item 또는 index 속성으로 지정된 이름
     * @param newPlaceholder foreach 내에서 고유하게 사용될 item, index의 새로운 이름.
     * @return ':item', ':index' 형태로 사용된 placeholder들을 모두 찾아서 새로 부여된 이름의 item, index로 바꾼 SQL을 반환.
     */
    private String expandLocalPlaceholder(String generated, String placeholder, String newPlaceholder) {
        String regex = ":" + placeholder + "\\W"; // 항상 id 뒤에 문자 하나는 있을 것이다. 호출되기 전에 " "을 하나 붙이기 때문에
        String replace = ":" + newPlaceholder;

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(generated);
        int begin = 0;
        while (true) {
            if (matcher.find(begin)) {
                generated = generated.substring(0, matcher.start()) + replace + generated.substring(matcher.end() - 1);

                begin = matcher.start() + replace.length();
                matcher = pattern.matcher(generated);
            } else {
                return generated; // 더 이상 없으면 중단.
            }
        }
    }
}
