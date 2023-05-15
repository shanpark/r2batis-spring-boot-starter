package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import lombok.Getter;
import ognl.Ognl;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public final class Foreach extends SqlNode {

    // 참고로 MyBatis는 nullable 속성이 하나 더 있다. 하지만 이건 nullable은 true이다.

    private final String collection; // required
    private final String item; // required
    private final String index;
    private final String open;
    private final String close;
    private final String separator;

    private final String uniqueId;
    private final List<SqlNode> sqlNodes = new ArrayList<>();
    private Collection<?> collectionParam = null;

    public Foreach(Element element) {
        collection = element.getAttribute("collection");
        if (collection.isBlank())
            throw new RuntimeException("The <foreach> element must include the 'collection' attribute.");
        item = element.getAttribute("item");
        index = element.getAttribute("index");
        if (item.isBlank() && index.isBlank())
            throw new RuntimeException("The <foreach> element must include the 'item' or 'index' attributes.");

        open = element.getAttribute("open");
        close = element.getAttribute("close");
        separator = element.getAttribute("separator");
        uniqueId = "_" + uid + "_";

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
    public void evaluateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, Map<String, Class<?>> placeholderMap, Map<String, Object> paramMap) {
        collectionParam = null;

        // collection에 지정된 건 argument.field 일 수도 있으므로 Ognl이 적용되어야 한다.
        String paramName = collection.split("\\.")[0].trim();
        for (MethodImpl.ParamInfo paramInfo : paramInfos) {
            if (paramName.equals(paramInfo.getName())) {
                try {
                    collectionParam = (Collection<?>) Ognl.getValue(collection, paramMap); // 가져온 값은 반드시 Collection 이어야 한다. 아니면 ClassCaseException이 발생하겠지.
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (collectionParam != null && !collectionParam.isEmpty()) {
            paramInfos = Arrays.copyOf(paramInfos, paramInfos.length + 2);
            paramInfos[paramInfos.length - 2] = new MethodImpl.ParamInfo(index, Integer.class); // parameter로 index속성에 지정된 이름으로 parameter가 전달된 것처럼 하나 만들어 넣어준다.

            args = Arrays.copyOf(args, args.length + 2);

            Map<String, Class<?>> tempPlaceholderMap = new HashMap<>();
            Map<String, Object> tempParamMap = new HashMap<>();

            int inx = 0;
            for (Object collectionItem : collectionParam) {
                // 실제 generate할 때 :item.xxx 를 모두 찾아서 placeholder와 그 값, 타입을 생성해서 map에 넣어줘야 한다.
                // :index도 모두 다른 이름으로 placeholder를 생성해주고 값과, 타입(Integer.class) 생성해서 map에 넣어줘야 한다.
                paramInfos[paramInfos.length - 1] = new MethodImpl.ParamInfo(item, collectionItem.getClass()); // parameter로 item속성에 지정된 이름으로 parameter가 전달된 것처럼 하나 만들어 넣어준다.

                args[args.length - 2] = inx; // 실제 전달된 것처럼 argument를 만들어 넣는다.
                args[args.length - 1] = collectionItem; // 실제 전달된 것처럼 argument를 만들어 넣는다.

                tempPlaceholderMap.clear();
                tempParamMap.clear();
                Query.initParamMap(tempParamMap, paramInfos, args); // foreach 하위의 구문은 다시 item과 index를 넣고 다시 초기화 해서 evaluate해야 한다.
                for (SqlNode sqlNode : sqlNodes)
                    sqlNode.evaluateSql(paramInfos, args, tempPlaceholderMap, tempParamMap); // 이 foreach 안에서 item을 이용하는 foreach가 이중으로 있을 수 있기 때문에 collectionItem마다 해줘야 한다.

                for (Map.Entry<String, Class<?>> entry : tempPlaceholderMap.entrySet()) {
                    String key = entry.getKey();
                    String newItemName = getNewItemName(inx); // collectionItem의 새이름이다.
                    String newIndexName = getNewIndexName(inx); // collectionItem의 새이름이다.

                    // 사용된 item, index 를 생성될 새 이름으로 변환한다.
                    if (key.equals(item) || key.startsWith(item + "."))
                        key = newItemName + key.substring(item.length()); // item => :item_n_i, item.field => :item_n_i.field 형태로 변환.
                    else if (key.equals(index))
                        key = newIndexName; // index => :index_n_i:형태로 변환.

                    placeholderMap.put(key, entry.getValue());

                    if (!item.isBlank()) // item이 지정되지 않았으면 굳이 만들어 넣을 필요 없다.
                        paramMap.putIfAbsent(newItemName, collectionItem);
                    if (!index.isBlank()) // index가 지정되지 않았으면 굳이 만들어 넣을 필요 없다.
                        paramMap.putIfAbsent(newIndexName, inx);
                }

                inx++;
            }
        }
    }

    @Override
    public String generateSql(Map<String, Object> paramMap, Set<String> bindSet) {
        assert !item.isBlank();

        if (collectionParam != null && !collectionParam.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            StringBuilder tempSb = new StringBuilder();

            int inx = 0;
            Object prevItem = null;
            Object prevIndex = null;
            for (Object element : collectionParam) {
                // for loop 안에서 참조하는 item, index 값들을 마치 param에 전달된 것처럼 만들어서 넣어준다. <if>의 test 조건같은
                // 곳에서 사용될 수 있기 때문에 만들어서 넣어줘야 한다.
                if (!item.isBlank())
                    prevItem = paramMap.putIfAbsent(item, element); // collection의 원소를 item 이름으로 넣어준다.
                if (!index.isBlank())
                    prevIndex = paramMap.putIfAbsent(index, inx); // collection의 index를 index 이름으로 넣어준다.

                // 실제 SQL 생성.
                Set<String> tempBindSet = new HashSet<>();
                for (SqlNode sqlNode : sqlNodes) {
                    String sql = sqlNode.generateSql(paramMap, tempBindSet); // generateSql()은 trim을 해서 보내므로 따로 trim()은 필요없다.
                    if (!sql.isBlank()) {
                        tempSb.append(sql).append(separator);
                        for (String key : tempBindSet) {
                            String newItemName = getNewItemName(inx); // collectionItem의 새 이름이다.
                            String newIndexName = getNewIndexName(inx); // index의 새 이름이다.
                            if (key.equals(item) || key.startsWith(item + "."))
                                key = newItemName + key.substring(item.length()); // item => :item_n_i, item.field => :item_n_i.field 형태로 변환.
                            else if (key.equals(index))
                                key = newIndexName; // index => :index_n_i:형태로 변환.

                            bindSet.add(key);
                        }
                    }
                }
                sb.append(expandPlaceholder(tempSb.toString(), inx));
                tempSb.setLength(0);

                // loop 처음에 임시로 넣어준 for loop 안에서 참조하는 item, index 값들을 다시 복구해준다.
                if (prevItem != null)
                    paramMap.put(item, prevItem); // 이전에 이미 있던 item 이 있으면 다시 넣어준다.
                else
                    paramMap.remove(item); // 이전에 값이 없었으면 내가 넣은 item은 삭제해준다.

                if (prevIndex != null)
                    paramMap.put(index, prevIndex); // 이전에 이미 있던 item 이 있으면 다시 넣어준다.
                else
                    paramMap.remove(index); // 이전에 값이 없었으면 내가 넣은 item은 삭제해준다.
                inx++;
            }
            if (!sb.toString().isBlank())
                sb.setLength(sb.length() - separator.length());

            return ((((open != null) && !open.isBlank()) ? open + " " : "") +
                    sb +
                    (((close != null) && !close.isBlank()) ? " " + close : "")).trim();
        } else {
            return "";
        }
    }

    private String getNewItemName(int inx) {
        return item + uniqueId + inx;
    }
    private String getNewIndexName(int inx) {
        return index + uniqueId + inx;
    }

    private String expandPlaceholder(String generated, int inx) {
        String regex = ":" + item + "\\W"; // 항상 id 뒤에 문자 하나는 있을 것이다. 호출되기 전에 " "을 하나 붙이기 때문에
        String replace = ":" + getNewItemName(inx);

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
