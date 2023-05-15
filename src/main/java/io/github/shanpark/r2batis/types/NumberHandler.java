package io.github.shanpark.r2batis.types;

import java.time.*;

/**
 * primitive 타입과 Number 상속 클래스들을 모두 담당하는 Handler이다.
 * primitive 타입과 Number 상속 클래스들을 모두 지원해야 하므로 정확하게 class 를 비교하지 않고
 * isAssignableFrom()을 사용해서 지원 여부를 결정한다.
 * 따라서 TypeUtils 의 support 목록에서 가장 마지막에 놓는 게 좋다.
 */
public class NumberHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return Number.class.isAssignableFrom(clazz); // primitive 타입도 지원해야 해서 isAssignableFrom() 사용.
    }

    @Override
    public Object convert(Object value, Class<?> targetClazz) {
        if (Byte.class.equals(targetClazz) || byte.class.equals(targetClazz))
            return ((Number) value).byteValue();
        else if (Short.class.equals(targetClazz) || short.class.equals(targetClazz))
            return ((Number) value).shortValue();
        else if (Integer.class.equals(targetClazz) || int.class.equals(targetClazz))
            return ((Number) value).intValue();
        else if (Long.class.equals(targetClazz) || long.class.equals(targetClazz))
            return ((Number) value).longValue();
        else if (Float.class.equals(targetClazz) || float.class.equals(targetClazz))
            return ((Number) value).floatValue();
        else if (Double.class.equals(targetClazz) || double.class.equals(targetClazz))
            return ((Number) value).doubleValue();
        else if (Character.class.equals(targetClazz) || char.class.equals(targetClazz))
            return (char) ((Number) value).intValue();
        else if (Boolean.class.equals(targetClazz) || boolean.class.equals(targetClazz))
            return ((Number) value).longValue() != 0;
        else if (String.class.equals(targetClazz))
            return value.toString();
        else if (ZonedDateTime.class.equals(targetClazz))
            return Instant.ofEpochMilli(((Number) value).longValue()).atZone(ZoneId.systemDefault());
        else if (LocalDateTime.class.equals(targetClazz))
            return Instant.ofEpochMilli(((Number) value).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        else if (LocalDate.class.equals(targetClazz))
            return Instant.ofEpochMilli(((Number) value).longValue()).atZone(ZoneId.systemDefault()).toLocalDate();
        else if (java.sql.Date.class.equals(targetClazz))
            return new java.sql.Date(((Number) value).longValue());
        else if (java.util.Date.class.equals(targetClazz))
            return new java.util.Date(((Number) value).longValue());
        else if (Instant.class.equals(targetClazz))
            return Instant.ofEpochMilli(((Number) value).longValue());

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClazz.getName()));
    }
}
