package io.github.shanpark.r2batis.types;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class StringHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return String.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClazz) {
        if (Byte.class.equals(targetClazz) || byte.class.equals(targetClazz))
            return Byte.parseByte((String) value);
        else if (Short.class.equals(targetClazz) || short.class.equals(targetClazz))
            return Short.parseShort((String) value);
        else if (Integer.class.equals(targetClazz) || int.class.equals(targetClazz))
            return Integer.parseInt((String) value);
        else if (Long.class.equals(targetClazz) || long.class.equals(targetClazz))
            return Long.parseLong((String) value);
        else if (Float.class.equals(targetClazz) || float.class.equals(targetClazz))
            return Float.parseFloat((String) value);
        else if (Double.class.equals(targetClazz) || double.class.equals(targetClazz))
            return Double.parseDouble((String) value);
        else if (Character.class.equals(targetClazz) || char.class.equals(targetClazz)) {
            if (((String) value).length() == 1)
                return ((String) value).charAt(0);
        }
        else if (Boolean.class.equals(targetClazz) || boolean.class.equals(targetClazz))
            return Boolean.parseBoolean((String) value);
        else if (String.class.equals(targetClazz))
            return value;
        else if (ZonedDateTime.class.equals(targetClazz))
            return ((String) value).length() > 19
                    ? ZonedDateTime.parse(((String) value), DateTimeFormatter.ISO_OFFSET_DATE_TIME) // 2023-12-03T10:15:30+09:00
                    : LocalDateTime.parse(((String) value), DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(ZoneId.systemDefault()); // 2033-12-03T10:15:30
        else if (LocalDateTime.class.equals(targetClazz))
            return ((String) value).length() > 19
                    ? ZonedDateTime.parse(((String) value), DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime() // 2023-12-03T10:15:30+09:00
                    : LocalDateTime.parse(((String) value), DateTimeFormatter.ISO_LOCAL_DATE_TIME); // 2033-12-03T10:15:30
        else if (LocalDate.class.equals(targetClazz))
            return LocalDate.parse((String) value, DateTimeFormatter.ISO_LOCAL_DATE);
        else if (java.sql.Date.class.equals(targetClazz))
            return java.sql.Date.valueOf((String) value); // 2023-12-03
        else if (java.util.Date.class.equals(targetClazz))
            return java.util.Date.from(toInstant((String) value));
        else if (Instant.class.equals(targetClazz))
            return toInstant((String) value);

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClazz.getName()));
    }

    private Instant toInstant(String value) {
        return value.length() > 19
                ? ZonedDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() // 2023-12-03T10:15:30+09:00
                : LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(ZoneId.systemDefault()).toInstant(); // 2033-12-03T10:15:30
    }
}
