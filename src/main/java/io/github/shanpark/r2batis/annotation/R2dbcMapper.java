package io.github.shanpark.r2batis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
public @interface R2dbcMapper {
    String connectionFactory() default "";
    String r2batisProperties() default "";
}
