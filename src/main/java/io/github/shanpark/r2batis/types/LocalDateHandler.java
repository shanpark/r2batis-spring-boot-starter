package io.github.shanpark.r2batis.types;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class LocalDateHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return LocalDate.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClazz) {
        // Long 보다 작은 타입으로 변환 불가.
        if (Long.class.equals(targetClazz) || long.class.equals(targetClazz))
            return ((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        else if (Float.class.equals(targetClazz) || float.class.equals(targetClazz))
            return (float) ((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        else if (Double.class.equals(targetClazz) || double.class.equals(targetClazz))
            return (double) ((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        else if (String.class.equals(targetClazz))
            return ((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE); // 시간정보가 없으면 offset은 의미없다.
        else if (ZonedDateTime.class.equals(targetClazz))
            return ((LocalDate) value).atStartOfDay(ZoneId.systemDefault());
        else if (LocalDateTime.class.equals(targetClazz))
            return LocalDateTime.of((LocalDate) value, LocalTime.MIN);
        else if (LocalDate.class.equals(targetClazz))
            return value;
        else if (java.sql.Date.class.equals(targetClazz))
            return java.sql.Date.valueOf((LocalDate) value);
        else if (java.util.Date.class.equals(targetClazz))
            return java.util.Date.from(((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant());
        else if (Instant.class.equals(targetClazz))
            return ((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant();

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClazz.getName()));
    }
}
