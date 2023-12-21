package io.github.shanpark.r2batis.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@SuppressWarnings("unused")
@Data
@Builder
@AllArgsConstructor
public class R2batisProperties {

    private String mapperLocations;
    private boolean mapUnderscoreToCamelCase;

    public R2batisProperties() {
        mapperLocations = "classpath:mapper/**/*.xml";
        mapUnderscoreToCamelCase = false;
    }
}
