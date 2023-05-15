package io.github.shanpark.r2batis.types;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class InstantHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return Instant.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClazz) {
        // Long 보다 작은 타입으로 변환 불가.
        if (Long.class.equals(targetClazz) || long.class.equals(targetClazz))
            return ((Instant) value).toEpochMilli();
        else if (Float.class.equals(targetClazz) || float.class.equals(targetClazz))
            return (float) ((Instant) value).toEpochMilli();
        else if (Double.class.equals(targetClazz) || double.class.equals(targetClazz))
            return (double) ((Instant) value).toEpochMilli();
        else if (String.class.equals(targetClazz))
            return ((Instant) value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        else if (ZonedDateTime.class.equals(targetClazz))
            return ((Instant) value).atZone(ZoneId.systemDefault());
        else if (LocalDateTime.class.equals(targetClazz))
            return LocalDateTime.ofInstant((Instant) value, ZoneId.systemDefault());
        else if (LocalDate.class.equals(targetClazz))
            return LocalDate.ofInstant((Instant) value, ZoneId.systemDefault());
        else if (java.sql.Date.class.equals(targetClazz))
            return java.sql.Date.valueOf(LocalDateTime.ofInstant((Instant) value, ZoneId.systemDefault()).toLocalDate());
        else if (java.util.Date.class.equals(targetClazz))
            return java.util.Date.from((Instant) value);
        else if (Instant.class.equals(targetClazz))
            return value;

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClazz.getName()));
    }
}
