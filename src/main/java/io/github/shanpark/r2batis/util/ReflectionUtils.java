package io.github.shanpark.r2batis.util;

import io.github.shanpark.r2batis.MethodImpl;
import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import ognl.Ognl;
import ognl.OgnlException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class ReflectionUtils {

    /**
     * 지정된 clazz 객체 타입을 생성하고 map에 담겨진 값들로 필드를 채워서 반환한다.
     * 만약 일반 POJO 객체가 아닌 DB의 primitive 타입이라면 Map에 담겨진 첫번째 값을
     * 갖는 primitive 객체가 반환될 것이다.
     *
     * @param map 객체의 각 필드를 채울 값이 담겨진 Map. key가 field의 이름이 된다.
     * @param clazz 생성할 객체의 타입 Class
     * @return 생성되어 값이 채워진 객체.
     */
    public static Object newInstanceFromMap(Map<String, Object> map, Class<?> clazz) {
        try {
            if (TypeUtils.supports(clazz)) {
                return TypeUtils.convert(map.values().iterator().next(), clazz);
            } else {
                Object obj = clazz.getDeclaredConstructor().newInstance();
                for (Map.Entry<String, Object> entry : map.entrySet())
                    setFieldValue(obj, entry.getKey(), entry.getValue());
                return obj;
            }
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchFieldException e) {
            throw new InvalidMapperElementException(e);
        }
    }

    /**
     * host 객체의 field에 값을 set한다.
     *
     * @param host target host 객체
     * @param fieldName 값을 setting할 필드의 이름.
     * @param value setting할 값 객체
     */
    public static void setFieldValue(Object host, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = host.getClass().getDeclaredField(fieldName);
        boolean accessibility = field.canAccess(host);
        if (!accessibility)
            field.setAccessible(true);
        field.set(host, TypeUtils.convert(value, field.getType())); // 무조건 지원하는 타입이어야 한다. 그렇지 않으면 exception 발생.
        if (!accessibility)
            field.setAccessible(false);
    }

    public static Class<?> getFieldsType(String[] fields, MethodImpl.ParamInfo[] parameters, int orgArgCount) {
        // interface 메소드로 넘어온 parameter중에서 place holder과 같은 이름의 parameter를 찾는다.
        int inx;
        for (inx = parameters.length - 1; inx >= 0; inx--) { // 뒤에 붙은 param들이 우선순위가 더 높기때문에 반드시 뒤에서부터 검색.
            if (fields[0].equals(parameters[inx].getName()))
                break;
        }

        if (inx >= 0) { // 같은 이름의 parameter 찾음.
            return ReflectionUtils.getFieldType(parameters[inx].getType(), fields, 1);
        } else { // 같은 이름의 parameter 못찾음.
            if (orgArgCount == 1) { // 맞는 parameter를 못찾았지만 최초 argument가 1개인 경우
                if (!TypeUtils.supports(parameters[0].getType())) { // 지원하는 primitive 타입이 아니라면 그 parameter각 POJO 객체라고 보고 그 객체의 field 중에서 찾는다.
                    return ReflectionUtils.getFieldType(parameters[0].getType(), fields, 0);
                }
            }
            // 여기까지 왔으면 맞는 parameter가 없다는 뜻이다. 인터페이스 선언이나 xml mapper 선언에서 이름이 틀린 것이다.
            throw new InvalidMapperElementException(String.format("Can't bind ':%s' parameter.", String.join(".", fields)));
        }
    }

    /**
     * 객체의 필드값을 찾아서 값과 Class 객체를 tuple로 반환한다.
     * "obj.field1.field2" 같은 접근이라면 recursive하게 하위 필드를 찾아서 계속 호출된다.
     *
     * @return 찾아진 field의 값과 그 값의 Class 객체를 담은 tuple. 값이 null이 더라도 Class 객체는 유효하다.
     */
    public static Class<?> getFieldType(Class<?> clazz, String[] fields, int fromIndex) {
        if (fields.length == fromIndex) {
            return clazz;
        } else { // if (fields.length > fromIndex)
            try {
                Method getter = getGetterMethod(clazz, fields[fromIndex]);
                return getFieldType(getter.getReturnType(), fields, fromIndex + 1);
            } catch (NoSuchMethodException e) {
                throw new InvalidMapperElementException(String.format("'%s' field of '%s' not found", fields[fromIndex], String.join(".", fields)), e);
            }
        }
    }

    public static Method getGetterMethod(Class<?> clazz, String fieldName) throws NoSuchMethodException {
        String getterMethodName;
        Method getterMethod;
        try {
            getterMethodName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            getterMethod = clazz.getMethod(getterMethodName);
        } catch (NoSuchMethodException e) {
            if (Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz)) {
                getterMethodName = "is" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                getterMethod = clazz.getMethod(getterMethodName);
            } else {
                throw e;
            }
        }
        return getterMethod;
    }

    public static Object findArgument(String name, MethodImpl.ParamInfo[] paramInfos, Object[] arguments, int orgArgCount) {
        // interface 메소드로 넘어온 parameter중에서 placeholder와 같은 이름의 parameter를 찾는다.
        // paramInfos, arguments는 로컬 변수를 위한 stack 역할도 하기때문에 항상 뒤에서부터 검색을 해야한다.
        int inx;
        for (inx = paramInfos.length - 1; inx >= 0 ; inx--) {
            if (name.equals(paramInfos[inx].getName()))
                break;
        }

        if (inx >= 0) { // 같은 이름의 parameter 찾음.
            return arguments[inx];
        } else { // 같은 이름의 parameter 못찾음.
            if (orgArgCount == 1) { // 맞는 parameter를 못찾았지만 최초 argument가 1개인 경우
                if (!TypeUtils.supports(paramInfos[0].getType())) { // 지원하는 primitive 타입이 아니라면 그 parameter각 POJO 객체라고 보고 그 객체의 field 중에서 찾는다.
                    try {
                        return Ognl.getValue(name, arguments[0]);
                    } catch (OgnlException e) {
                        throw new InvalidMapperElementException(String.format("Can't bind ':%s' parameter.", name), e);
                    }
                }
            }
            // 여기까지 왔으면 맞는 parameter가 없다는 뜻이다. 인터페이스 선언이나 xml mapper 선언에서 이름이 틀린 것이다.
            throw new InvalidMapperElementException(String.format("Can't bind ':%s' parameter.", name));
        }
    }
}
