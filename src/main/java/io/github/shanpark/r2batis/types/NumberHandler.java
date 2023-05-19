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
    public Object convert(Object value, Class<?> targetClass) {
        if (Byte.class.equals(targetClass) || byte.class.equals(targetClass))
            return ((Number) value).byteValue();
        else if (Short.class.equals(targetClass) || short.class.equals(targetClass))
            return ((Number) value).shortValue();
        else if (Integer.class.equals(targetClass) || int.class.equals(targetClass))
            return ((Number) value).intValue();
        else if (Long.class.equals(targetClass) || long.class.equals(targetClass))
            return ((Number) value).longValue();
        else if (Float.class.equals(targetClass) || float.class.equals(targetClass))
            return ((Number) value).floatValue();
        else if (Double.class.equals(targetClass) || double.class.equals(targetClass))
            return ((Number) value).doubleValue();
        else if (Character.class.equals(targetClass) || char.class.equals(targetClass))
            return (char) ((Number) value).intValue();
        else if (Boolean.class.equals(targetClass) || boolean.class.equals(targetClass))
            return ((Number) value).longValue() != 0;
        else if (String.class.equals(targetClass))
            return value.toString();
        else if (ZonedDateTime.class.equals(targetClass))
            return Instant.ofEpochMilli(((Number) value).longValue()).atZone(ZoneId.systemDefault());
        else if (LocalDateTime.class.equals(targetClass))
            return Instant.ofEpochMilli(((Number) value).longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        else if (LocalDate.class.equals(targetClass))
            return Instant.ofEpochMilli(((Number) value).longValue()).atZone(ZoneId.systemDefault()).toLocalDate();
        else if (java.sql.Date.class.equals(targetClass))
            return new java.sql.Date(((Number) value).longValue());
        else if (java.util.Date.class.equals(targetClass))
            return new java.util.Date(((Number) value).longValue());
        else if (Instant.class.equals(targetClass))
            return Instant.ofEpochMilli(((Number) value).longValue());

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClass.getName()));
    }
}
