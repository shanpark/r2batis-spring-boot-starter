package io.github.shanpark.r2batis.types;

import java.time.*;
import java.util.Date;

public class DateHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return Date.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClass) {
        // Long 보다 작은 타입으로 변환 불가.
        if (Long.class.equals(targetClass) || long.class.equals(targetClass))
            return ((Date) value).getTime();
        else if (Float.class.equals(targetClass) || float.class.equals(targetClass))
            return (float) ((Date) value).getTime();
        else if (String.class.equals(targetClass))
            return value.toString();
        else if (ZonedDateTime.class.equals(targetClass))
            return ((Date) value).toInstant().atZone(ZoneId.systemDefault());
        else if (LocalDateTime.class.equals(targetClass))
            return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        else if (LocalDate.class.equals(targetClass))
            return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        else if (java.sql.Date.class.equals(targetClass))
            return new java.sql.Date(((Date) value).getTime());
        else if (Date.class.equals(targetClass))
            return value;
        else if (Instant.class.equals(targetClass))
            return ((Date) value).toInstant();

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClass.getName()));
    }
}
