package io.github.shanpark.r2batis.types;

public class BooleanHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return Boolean.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClass) {
        if (Byte.class.equals(targetClass) || byte.class.equals(targetClass))
            return (Boolean) value ? (byte) 1 : (byte) 0;
        else if (Short.class.equals(targetClass) || short.class.equals(targetClass))
            return (Boolean) value ? (short) 1 : (short) 0;
        else if (Integer.class.equals(targetClass) || int.class.equals(targetClass))
            return (Boolean) value ? 1 : 0;
        else if (Long.class.equals(targetClass) || long.class.equals(targetClass))
            return (Boolean) value ? 1L : 0L;
        else if (Float.class.equals(targetClass) || float.class.equals(targetClass))
            return (Boolean) value ? 1.0f : 0.0f;
        else if (Double.class.equals(targetClass) || double.class.equals(targetClass))
            return (Boolean) value ? 1.0 : 0.0;
        else if (Character.class.equals(targetClass) || char.class.equals(targetClass))
            return (Boolean) value ? 'T' : 'F';
        else if (Boolean.class.equals(targetClass) || boolean.class.equals(targetClass))
            return value;
        else if (String.class.equals(targetClass))
            return value.toString();

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClass.getName()));
    }
}
