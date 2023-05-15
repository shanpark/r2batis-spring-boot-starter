package io.github.shanpark.r2batis.types;

public interface TypeHandler {
    boolean canHandle(Class<?> clazz);
    Object convert(Object value, Class<?> targetClazz);
}
