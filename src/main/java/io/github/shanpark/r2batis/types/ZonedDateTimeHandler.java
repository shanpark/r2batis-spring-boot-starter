package io.github.shanpark.r2batis.types;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ZonedDateTimeHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return ZonedDateTime.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClazz) {
        // Long 보다 작은 타입으로 변환 불가.
        if (Long.class.equals(targetClazz) || long.class.equals(targetClazz))
            return ((ZonedDateTime) value).toInstant().toEpochMilli();
        else if (Float.class.equals(targetClazz) || float.class.equals(targetClazz))
            return (float) ((ZonedDateTime) value).toInstant().toEpochMilli();
        else if (Double.class.equals(targetClazz) || double.class.equals(targetClazz))
            return (double) ((ZonedDateTime) value).toInstant().toEpochMilli();
        else if (String.class.equals(targetClazz))
            return ((ZonedDateTime) value).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        else if (ZonedDateTime.class.equals(targetClazz))
            return value;
        else if (LocalDateTime.class.equals(targetClazz))
            return ((ZonedDateTime) value).toLocalDateTime();
        else if (LocalDate.class.equals(targetClazz))
            return ((ZonedDateTime) value).toLocalDate();
        else if (java.sql.Date.class.equals(targetClazz))
            return java.sql.Date.valueOf(((ZonedDateTime) value).toLocalDateTime().toLocalDate());
        else if (java.util.Date.class.equals(targetClazz))
            return java.util.Date.from(((ZonedDateTime) value).toInstant());
        else if (Instant.class.equals(targetClazz))
            return ((ZonedDateTime) value).toInstant();

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClazz.getName()));
    }
}
