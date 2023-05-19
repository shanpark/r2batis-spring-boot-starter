package io.github.shanpark.r2batis.types;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class LocalDateHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return LocalDate.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClass) {
        // Long 보다 작은 타입으로 변환 불가.
        if (Long.class.equals(targetClass) || long.class.equals(targetClass))
            return ((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        else if (Float.class.equals(targetClass) || float.class.equals(targetClass))
            return (float) ((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        else if (Double.class.equals(targetClass) || double.class.equals(targetClass))
            return (double) ((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        else if (String.class.equals(targetClass))
            return ((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE); // 시간정보가 없으면 offset은 의미없다.
        else if (ZonedDateTime.class.equals(targetClass))
            return ((LocalDate) value).atStartOfDay(ZoneId.systemDefault());
        else if (LocalDateTime.class.equals(targetClass))
            return LocalDateTime.of((LocalDate) value, LocalTime.MIN);
        else if (LocalDate.class.equals(targetClass))
            return value;
        else if (java.sql.Date.class.equals(targetClass))
            return java.sql.Date.valueOf((LocalDate) value);
        else if (java.util.Date.class.equals(targetClass))
            return java.util.Date.from(((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant());
        else if (Instant.class.equals(targetClass))
            return ((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant();

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClass.getName()));
    }
}
