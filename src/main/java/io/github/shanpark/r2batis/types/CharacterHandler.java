package io.github.shanpark.r2batis.types;

public class CharacterHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return CharacterHandler.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClass) {
        if (Short.class.equals(targetClass) || short.class.equals(targetClass))
            return (short) ((Character) value).charValue();
        else if (Integer.class.equals(targetClass) || int.class.equals(targetClass))
            return (int) (Character) value;
        else if (Long.class.equals(targetClass) || long.class.equals(targetClass))
            return (long) (Character) value;
        else if (Float.class.equals(targetClass) || float.class.equals(targetClass))
            return (float) (Character) value;
        else if (Double.class.equals(targetClass) || double.class.equals(targetClass))
            return (double) (Character) value;
        else if (Character.class.equals(targetClass) || char.class.equals(targetClass))
            return value;
        else if (Boolean.class.equals(targetClass) || boolean.class.equals(targetClass)) // only 'T' is true.
            return ((Character) value) == 'T' || ((Character) value) == 't';
        else if (String.class.equals(targetClass))
            return value.toString();

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClass.getName()));
    }
}
