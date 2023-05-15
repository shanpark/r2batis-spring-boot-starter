package io.github.shanpark.r2batis.types;

public class BooleanHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return Boolean.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClazz) {
        if (Byte.class.equals(targetClazz) || byte.class.equals(targetClazz))
            return (Boolean) value ? (byte) 1 : (byte) 0;
        else if (Short.class.equals(targetClazz) || short.class.equals(targetClazz))
            return (Boolean) value ? (short) 1 : (short) 0;
        else if (Integer.class.equals(targetClazz) || int.class.equals(targetClazz))
            return (Boolean) value ? 1 : 0;
        else if (Long.class.equals(targetClazz) || long.class.equals(targetClazz))
            return (Boolean) value ? 1L : 0L;
        else if (Float.class.equals(targetClazz) || float.class.equals(targetClazz))
            return (Boolean) value ? 1.0f : 0.0f;
        else if (Double.class.equals(targetClazz) || double.class.equals(targetClazz))
            return (Boolean) value ? 1.0 : 0.0;
        else if (Character.class.equals(targetClazz) || char.class.equals(targetClazz))
            return (Boolean) value ? 'T' : 'F';
        else if (Boolean.class.equals(targetClazz) || boolean.class.equals(targetClazz))
            return value;
        else if (String.class.equals(targetClazz))
            return value.toString();

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClazz.getName()));
    }
}
