package io.github.shanpark.r2batis.types;

public class CharacterHandler implements TypeHandler {
    @Override
    public boolean canHandle(Class<?> clazz) {
        return CharacterHandler.class.equals(clazz);
    }

    @Override
    public Object convert(Object value, Class<?> targetClazz) {
        if (Short.class.equals(targetClazz) || short.class.equals(targetClazz))
            return (short) ((Character) value).charValue();
        else if (Integer.class.equals(targetClazz) || int.class.equals(targetClazz))
            return (int) (Character) value;
        else if (Long.class.equals(targetClazz) || long.class.equals(targetClazz))
            return (long) (Character) value;
        else if (Float.class.equals(targetClazz) || float.class.equals(targetClazz))
            return (float) (Character) value;
        else if (Double.class.equals(targetClazz) || double.class.equals(targetClazz))
            return (double) (Character) value;
        else if (Character.class.equals(targetClazz) || char.class.equals(targetClazz))
            return value;
        else if (Boolean.class.equals(targetClazz) || boolean.class.equals(targetClazz)) // only 'T' is true.
            return ((Character) value) == 'T' || ((Character) value) == 't';
        else if (String.class.equals(targetClazz))
            return value.toString();

        throw new ClassCastException(String.format("Can't cast '%s' to type '%s'.", value, targetClazz.getName()));
    }
}
