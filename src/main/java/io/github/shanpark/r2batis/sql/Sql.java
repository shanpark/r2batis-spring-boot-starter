package io.github.shanpark.r2batis.sql;

import io.github.shanpark.r2batis.MethodImpl;
import io.github.shanpark.r2batis.util.ReflectionUtils;

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
                    paramMap.put(fields[0], ReflectionUtils.findArgument(fields[0], paramInfos, args));
                }
            }
        }
    }

    @Override
    public String generateSql(Map<String, Object> paramMap, Set<String> bindSet) {
        bindSet.addAll(placeholderSet); // 모두 binding 해야 함.
        return sql;
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
