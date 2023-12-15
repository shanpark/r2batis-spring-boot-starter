package io.github.shanpark.r2batis.mapper;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class Sql extends SqlNode {

    private static final String COLON_IDENTIFIER_REGEX = "^:[a-zA-Z_]\\w*(\\.[a-zA-Z_]\\w*)*$"; // ':someid.somefield' 등등
    private static final Pattern COLON_IDENTIFIER_PATTERN = Pattern.compile(COLON_IDENTIFIER_REGEX);

    private final String sql;
    private volatile Set<String> placeholderSet;

    public Sql(String sql) {
        this.sql = sql.trim();
    }

    @Override
    public String generateSql(MapperContext mapperContext) {
        if (!sql.isBlank()) {
            for (String placeholder : getPlaceholderSet()) {
                mapperContext.addPlaceholder(placeholder);
            }
        }
        return sql.trim();
    }

    private Set<String> getPlaceholderSet() {
        if (placeholderSet == null) { // 한 번 정해지면 바뀔 일이 없으므로 캐슁해서 가져온다.
            synchronized (this) {
                if (placeholderSet == null) {
                    placeholderSet = new HashSet<>();

                    String[] words = sql.split("[^A-Za-z\\d.:_]+"); // 모든 토큰 분리 간이 로직이다. 실제로는 더 정교해야 할 수도 있다.
                    for (String word : words) {
                        if (COLON_IDENTIFIER_PATTERN.matcher(word).matches()) {
                            String placeholder = word.substring(1); // ':' 뗴고 넣는다.
                            placeholderSet.add(placeholder);
                        }
                    }
                }
            }
        }
        return placeholderSet;
    }
}
