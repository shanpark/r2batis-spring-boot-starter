package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import io.github.shanpark.r2batis.util.ReflectionUtils;
import lombok.Getter;
import ognl.Ognl;
import ognl.OgnlException;
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
        uniqueId = "_" + uid.incrementAndGet() + "_";

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
        Collection<?> collectionParam;

        // collection에 지정된 파라메터를 가져온다. collection에 지정된 건 argument.field 일 수도 있으므로 Ognl이 적용되어야 한다.
        try {
            String[] fields = collection.split("\\.");
            if (fields.length == 1)
                collectionParam = (Collection<?>) ReflectionUtils.findArgument(collection, paramInfos, args, orgArgCount); // 가져온 값은 반드시 Collection 이어야 한다. 아니면 ClassCaseException이 발생하겠지.
            else
                collectionParam = (Collection<?>) Ognl.getValue(collection.substring(collection.indexOf('.') + 1), ReflectionUtils.findArgument(fields[0], paramInfos, args, orgArgCount));
        } catch (OgnlException e) {
            throw new InvalidMapperElementException(e);
        }

        if (!collectionParam.isEmpty()) {
            int addedArgs = 0; // item, index 속성이 설정되어 있으면 임시로 paramInfos, args에 해당 값을 추가하여 마치 처음부터 인자로 받은 것처럼 해서 하위 sql문들을 렌더링한다.
            if (!item.isBlank())
                addedArgs++;
            if (!index.isBlank())
                addedArgs++;

            if (addedArgs > 0) {
                paramInfos = Arrays.copyOf(paramInfos, paramInfos.length + addedArgs);
                args = Arrays.copyOf(args, args.length + addedArgs);

                if (!index.isBlank()) // index는 항상 integer 형이므로 미리 설정한다.
                    paramInfos[paramInfos.length - addedArgs] = new MethodImpl.ParamInfo(index, Integer.class); // parameter로 index속성에 지정된 이름으로 parameter가 전달된 것처럼 하나 만들어 넣어준다.
            }

            Map<String, Class<?>> tempPlaceholderMap = new HashMap<>();
            Map<String, Object> tempParamMap = new HashMap<>();
            int inx = 0;
            for (Object collectionItem : collectionParam) {
                // 실제 generate할 때 :item.xxx 를 모두 찾아서 placeholder와 그 값, 타입을 생성해서 map에 넣어줘야 한다.
                // :index도 모두 다른 이름으로 placeholder를 생성해주고 값과, 타입(Integer.class) 생성해서 map에 넣어줘야 한다.
                if (!item.isBlank()) {
                    paramInfos[paramInfos.length - 1] = new MethodImpl.ParamInfo(item, collectionItem.getClass()); // parameter로 item속성에 지정된 이름으로 parameter가 전달된 것처럼 하나 만들어 넣어준다.
                    args[args.length - 1] = collectionItem; // 실제 전달된 것처럼 argument를 만들어 넣는다.
                }
                if (!index.isBlank()) {
                    // paramInfos는 for loop 전에 이미 설정했다.
                    args[args.length - addedArgs] = inx; // 실제 전달된 것처럼 argument를 만들어 넣는다.
                }

                tempPlaceholderMap.clear();
                tempParamMap.clear();
                Query.initParamMap(tempParamMap, paramInfos, args); // foreach 하위의 구문은 다시 item과 index를 넣고 다시 초기화 해서 evaluate해야 한다.
                for (SqlNode sqlNode : sqlNodes)
                    sqlNode.evaluateSql(paramInfos, args, orgArgCount, tempPlaceholderMap, tempParamMap); // 이 foreach 안에서 item을 이용하는 foreach가 이중으로 있을 수 있기 때문에 collectionItem마다 해줘야 한다.

                for (Map.Entry<String, Class<?>> entry : tempPlaceholderMap.entrySet()) {
                    String key = entry.getKey();
                    String newItemName = getNewItemName(inx); // collectionItem의 새이름이다. item => :item_uid_inx 형태로 변환된다.
                    String newIndexName = getNewIndexName(inx); // index 변수의 새이름이다.

                    // 사용된 item, index 를 생성될 새 이름으로 변환한다.
                    // item, index가 지정되지 않았으면 item, index는 모두 공백이고 key는 공백일 수 없으므로 item, index가 지정되었는지 따로 체크하지 않는다.
                    if (key.equals(item) || key.startsWith(item + ".")) {
                        placeholderMap.put(newItemName + key.substring(item.length()), entry.getValue());
                        paramMap.put(newItemName, tempParamMap.get(item));
                    } else if (key.equals(index)) {
                        placeholderMap.put(newIndexName, entry.getValue());
                        paramMap.put(newIndexName, tempParamMap.get(index));
                    } else {
                        placeholderMap.put(key, entry.getValue());

                        String[] fields = key.split("\\.");
                        paramMap.put(fields[0], tempParamMap.get(fields[0])); // 항상 paramMap에는 placeholder의 fields[0]만 들어간다. ognl로 읽어오기 때문이다.
                    }
                }

                inx++;
            }
        }
    }

    @Override
    public String generateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, int orgArgCount, Map<String, Object> paramMap, Set<String> bindSet) {
        Collection<?> collectionParam;

        // collection에 지정된 건 argument.field 일 수도 있으므로 Ognl이 적용되어야 한다.
        try {
            String[] fields = collection.split("\\.");
            if (fields.length == 1)
                collectionParam = (Collection<?>) ReflectionUtils.findArgument(collection.trim(), paramInfos, args, orgArgCount); // 가져온 값은 반드시 Collection 이어야 한다. 아니면 ClassCaseException이 발생하겠지.
            else
                collectionParam = (Collection<?>) Ognl.getValue(collection.substring(collection.indexOf('.') + 1), ReflectionUtils.findArgument(fields[0], paramInfos, args, orgArgCount));
        } catch (OgnlException e) {
            throw new InvalidMapperElementException(e);
        }

        if (!collectionParam.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            StringBuilder tempSb = new StringBuilder();

            int addedArgs = 0; // item, index 속성이 설정되어 있으면 임시로 paramInfos, args에 해당 값을 추가하여 마치 처음부터 인자로 받은 것처럼 해서 하위 sql문들을 렌더링한다.
            if (!item.isBlank())
                addedArgs++;
            if (!index.isBlank())
                addedArgs++;

            if (addedArgs > 0) {
                paramInfos = Arrays.copyOf(paramInfos, paramInfos.length + addedArgs);
                args = Arrays.copyOf(args, args.length + addedArgs);

                if (!index.isBlank()) // index는 항상 integer 형이므로 미리 설정한다.
                    paramInfos[paramInfos.length - addedArgs] = new MethodImpl.ParamInfo(index, Integer.class); // parameter로 index속성에 지정된 이름으로 parameter가 전달된 것처럼 하나 만들어 넣어준다.
            }

            int inx = 0;
            for (Object collectionItem : collectionParam) {
                // for loop 안에서 참조하는 item, index 값들을 마치 param에 전달된 것처럼 만들어서 넣어준다. <if>의 test 조건같은
                // 곳에서 사용될 수 있기 때문에 만들어서 넣어줘야 한다.
                if (!item.isBlank()) {
                    paramInfos[paramInfos.length - 1] = new MethodImpl.ParamInfo(item, collectionItem.getClass()); // parameter로 item속성에 지정된 이름으로 parameter가 전달된 것처럼 하나 만들어 넣어준다.
                    args[args.length - 1] = collectionItem; // 실제 전달된 것처럼 argument를 만들어 넣는다.
                }
                if (!index.isBlank()) {
                    // paramInfos는 for loop 전에 이미 설정했다.
                    args[args.length - addedArgs] = inx; // 실제 전달된 것처럼 argument를 만들어 넣는다.
                }

                // 실제 SQL 생성.
                Set<String> tempBindSet = new HashSet<>();
                for (SqlNode sqlNode : sqlNodes) {
                    String sql = sqlNode.generateSql(paramInfos, args, orgArgCount, paramMap, tempBindSet); // generateSql()은 trim을 해서 보내므로 따로 trim()은 필요없다.
                    if (!sql.isBlank()) {
                        if (!sb.isEmpty())
                            tempSb.append(separator);
                        tempSb.append(sql);
                        for (String key : tempBindSet) {
                            String newItemName = getNewItemName(inx); // collectionItem의 새 이름이다.
                            String newIndexName = getNewIndexName(inx); // index의 새 이름이다.
                            if (key.equals(item) || key.startsWith(item + "."))
                                key = newItemName + key.substring(item.length()); // item => :item_uid_inx, item.field => :item_uid_inx.field 형태로 변환.
                            else if (key.equals(index))
                                key = newIndexName; // index => :index_uid_inx:형태로 변환.

                            bindSet.add(key);
                        }
                    }
                }
                tempSb.append(' '); // SQL의 맨 마지막에 있는 :item, :index 에 매칭하려면 맨 뒤에 공백이 하나 붙어야 한다.
                String sql = tempSb.toString();
                if (!item.isBlank())
                    sql = expandLocalPlaceholder(sql, item, getNewItemName(inx));
                if (!index.isBlank())
                    sql = expandLocalPlaceholder(sql, index, getNewIndexName(inx));
                sb.append(sql.trim());

                tempSb.setLength(0);
                inx++;
            }

            return ((open.isBlank() ? "" : open + " ") +
                    sb +
                    (close.isBlank() ? "" : " " + close)).trim();
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
