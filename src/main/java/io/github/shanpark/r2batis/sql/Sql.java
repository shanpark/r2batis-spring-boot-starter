package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import io.github.shanpark.r2batis.util.ReflectionUtils;
import io.github.shanpark.r2batis.util.TypeUtils;
import ognl.Ognl;
import ognl.OgnlException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class Sql extends SqlNode {

    private static final String COLON_IDENTIFIER_REGEX = "^:[a-zA-Z_]\\w*(\\.[a-zA-Z_]\\w*)*$"; // ':someid.somefield' 등등
    private static final Pattern COLON_IDENTIFIER_PATTERN = Pattern.compile(COLON_IDENTIFIER_REGEX);

    private final String sql;
    private Set<String> placeholderSet = null;

    public Sql(String sql) {
        this.sql = sql.trim();
    }

    @Override
    public void evaluateSql(MethodImpl.ParamInfo[] paramInfos, Object[] args, Map<String, Class<?>> placeholderMap, Map<String, Object> paramMap) {
        if ((sql != null) && (!sql.isBlank())) {
            for (String placeholder : getPlaceholderSet()) {
                if (!placeholderMap.containsKey(placeholder)) {
                    String[] fields = placeholder.split("\\.");
                    Class<?> type = ReflectionUtils.getFieldsType(fields, paramInfos);
                    placeholderMap.put(placeholder, type);
                    paramMap.put(fields[0], findArgument(fields[0], paramInfos, args));
                }
            }
        }
    }

    @Override
    public String generateSql(Map<String, Object> paramMap, Set<String> bindSet) {
        bindSet.addAll(placeholderSet); // 모두 binding 해야 함.
        return sql;
    }

    private Object findArgument(String name, MethodImpl.ParamInfo[] paramInfos, Object[] arguments) {
        // interface 메소드로 넘어온 parameter중에서 placeholder와 같은 이름의 parameter를 찾는다.
        int inx;
        for (inx = 0; inx < paramInfos.length; inx++) {
            if (name.equals(paramInfos[inx].getName()))
                break;
        }

        if (inx < paramInfos.length) { // 같은 이름의 parameter 찾음.
            return arguments[inx];
        } else { // 같은 이름의 parameter 못찾음.
            if (paramInfos.length == 1) { // 맞는 parameter를 못찾았지만 parameter가 1개인 경우
                if (!TypeUtils.supports(paramInfos[0].getType())) { // 지원하는 primitive 타입이 아니라면 그 parameter각 POJO 객체라고 보고 그 객체의 field 중에서 찾는다.
                    try {
                        return Ognl.getValue(name, arguments[0]);
                    } catch (OgnlException e) {
                        throw new RuntimeException(String.format("Can't bind ':%s' parameter.", name), e);
                    }
                }
            }
            // 여기까지 왔으면 맞는 parameter가 없다는 뜻이다. 인터페이스 선언이나 xml mapper 선언에서 이름이 틀린 것이다.
            throw new RuntimeException(String.format("Can't bind ':%s' parameter.", name));
        }
    }

    private Set<String> getPlaceholderSet() {
        if (placeholderSet == null) { // 한 번 정해지면 바뀔 일이 없으므로 캐슁하는게 맞다.
            placeholderSet = new HashSet<>();

            String[] words = sql.split("[^A-Za-z\\d.:_]+"); // 모든 토큰 분리 간이 로직이다. 실제로는 더 정교해야 할 수도 있다.
            for (String word : words) {
                if (COLON_IDENTIFIER_PATTERN.matcher(word).matches()) {
                    String placeholder = word.substring(1); // ':' 뗴고 넣는다.
                    placeholderSet.add(placeholder);
                }
            }
        }
        return placeholderSet;
    }
}
