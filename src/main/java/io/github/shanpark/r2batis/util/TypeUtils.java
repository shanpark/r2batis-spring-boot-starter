package io.github.shanpark.r2batis.util;

import io.github.shanpark.r2batis.types.*;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * R2DBC 드라이버가 지원하는 지원 타입들 간에 서로 변환해주는 Utility 클래스이다.
 * 구현체마다 사용하는 타입이 다르기 떄문에 일단 가능한 모든 타입을 구현해야 한다.
 * 예를 들어 같은 TIMESTAMP 컬럼을 쿼리해서 가져왔을 때 MySQL은 Instant를 반환하고
 * MariaDB는 LocalDateTime 값을 반환한다. 다른 DB 제조사의 구현도 또한 달라질 수
 * 있으므로 가능한 모든 타입에 대해서 구현을 해줘야 한다.
 */
public class TypeUtils {
    private static final List<TypeHandler> supports = Arrays.asList(
            new CharacterHandler(),
            new BooleanHandler(),
            new StringHandler(),
            new DateHandler(),
            new SqlDateHandler(),
            new ZonedDateTimeHandler(),
            new LocalDateTImeHandler(),
            new LocalDateHandler(),
            new InstantHandler(),
            new NumberHandler() // primitive와 각종 Number 상속 클래스들을 통으로 지원. 마지막에 넣는 게 좋다.
    );

    /**
     * TypeHandler series 클래스들이 지원하는 타입인지 체크.
     *
     * @param clazz 체크할 타입의 Class<?> 객체
     * @return 지원하는 타입이면 true, 그렇지 않으면 false
     */
    public static boolean supports(Class<?> clazz) {
        return supports.stream().anyMatch(handler -> handler.canHandle(clazz));
    }

    /**
     * 쿼리 결과를 Mapper 인터페이스가 지정하는 타입으로 변환하는 데 주로 사용되는 메소드이다.
     * 변환이 불가능한 source 값과 target 타입이 지정되면 ClassCastException이 발생한다.
     *
     * @param value 변환을 할 source 값 객체
     * @param targetClass 변환하고자 하는 target 타입 클래스 객체
     * @return targetClass로 변환된 값 객체.
     */
    public static Object convert(Object value, Class<?> targetClass) {
        Class<?> sourceClass = value.getClass();
        Optional<TypeHandler> typeHandler = supports.stream()
                .filter(handler -> handler.canHandle(sourceClass))
                .findFirst();

        if (typeHandler.isPresent())
            return typeHandler.get().convert(value, targetClass);

        throw new ClassCastException(String.format("Can't cast '%s' type value. No TypeHandler is available.", sourceClass.getName()));
    }

    /**
     * Mapper 인터페이스로 전달된 파라메터 객체가 R2DBC에서 지원하는 타입이 아닐 경우 지원 가능한 값으로 변환한다.
     * 예를 들어 java.sql.Date 같은 객체는 R2DBC에서는 지원하지 않으므로 LocalDate 객체로 자동변환해서 사용한다.
     * 만약 변환을 지원하지 않는다면 그냥 원래 값 그대로 반환한다.
     *
     * @param param 인터페이스로 전달된 파라메터 값 객체.
     * @return 변환을 지원하는 경우 변환된 객체. 그렇지 않은 경우 원본 객체.
     */
    public static Object convertForParam(Object param) {
        if (param instanceof java.sql.Date) // R2DBC does not support java.sql.Date.
            return ((java.sql.Date) param).toLocalDate();
        else if (param instanceof java.util.Date) // R2DBC does not support java.util.Date.
            return ((java.util.Date) param).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        return param;
    }
}
