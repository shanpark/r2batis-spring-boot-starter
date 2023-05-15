package io.github.shanpark.r2batis.types;

import java.time.*;

public class SqlDateHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return java.sql.Date.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClazz) {
        // Long 보다 작은 타입으로 변환 불가.
        if (Long.class.equals(targetClazz) || long.class.equals(targetClazz))
            return ((java.sql.Date) value).getTime();
        else if (Float.class.equals(targetClazz) || float.class.equals(targetClazz))
            return (float) ((java.sql.Date) value).getTime();
        else if (Double.class.equals(targetClazz) || double.class.equals(targetClazz))
            return (double) ((java.sql.Date) value).getTime();
        else if (String.class.equals(targetClazz))
            return value.toString();
        else if (ZonedDateTime.class.equals(targetClazz))
            return ((java.sql.Date) value).toInstant().atZone(ZoneId.systemDefault());
        else if (LocalDateTime.class.equals(targetClazz))
            return ((java.sql.Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        else if (LocalDate.class.equals(targetClazz))
            return ((java.sql.Date) value).toLocalDate();
        else if (java.sql.Date.class.equals(targetClazz))
            return value;
        else if (java.util.Date.class.equals(targetClazz))
            return new java.util.Date(((java.sql.Date) value).getTime());
        else if (Instant.class.equals(targetClazz))
            return ((java.sql.Date) value).toInstant();

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClazz.getName()));
    }
}
