[![](https://www.jitpack.io/v/shanpark/r2batis-spring-boot-starter.svg)](https://www.jitpack.io/#shanpark/r2batis-spring-boot-starter)

# R2Batis

Framework for Spring Data R2DBC (Similar to MyBatis)

## 0. Read please

1. All classes in the mapper XML file must use the full package path.    
   This is because there is no type alias feature.
2. For the same reason, use wrapper classes instead of primitive types.

## 1. Intall

### build.gradle
```
repositories {
    ...
    maven { url 'https://www.jitpack.io' }
}
```

```
dependencies {
    ...
    implementation 'org.springframework.boot:spring-boot-starter-data-r2dbc'
    implementation 'com.github.shanpark:r2batis-spring-boot-starter:0.1.5'
    // include vendor dependent R2DBC driver.
}
```

## 2. Configuration

### application.yml

```yaml
r2batis:
  mapper-locations: classpath:mapper/**/*.xml #, xxx, yyy, ...
  configuration:
    mapUnderscoreToCamelCase: false
```

## 3. Example


```java
@Data
public class CustomerVo {
    private Long id;
    private String name;
    private String email;
}
```

### Interface

```java
package com.example.mapper;

...

@R2dbcMapper
public interface CustomerMapper {
    Mono<CustomerVo> getCustomer(Long customerId);
    Flux<CustomerVo> getCustomerList();
    Mono<Integer> insertCustomer(CustomerVo customerVo);
}
```

### Mapper XML

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<mapper namespace="com.example.mapper.CustomerMapper" >

    <select id="getCustomer" resultType="com.example.vo.CustomerVo">
        SELECT *
        FROM Customer
        WHERE id = :customerId
    </select>

    <select id="getCustomerList" resultType="com.example.vo.CustomerVo">
        SELECT *
        FROM Customer
    </select>
    
    <insert id="insertCustomer" resultType="java.lang.Integer">
        <selectKey keyProperty="id" resultType="java.lang.Long" order="BEFORE">
            SELECT MAX(id) + 1
            FROM Customer
        </selectKey>

        INSERT INTO Customer
            (name, email)
        VALUES
            (:name, :email)
    </insert>
    
</mapper>
```

## 4. Notes

- The `keyColumn` attribute of the `<selectKey>` element can only contain a single column name.
- `<insert>`, `<update>`, `<delete>` elements return the number of affected rows.  
  And the result type is `Long`. (MySQL, MaraiDB implementation tested.)
- @R2dbdMapper 어노테이션 지정된 bean을 등록하는 시점이 ConnectionFactory 보다는 늦지만 다른 bean들 보다는 
  빨라야 한다. 이 시점을 맞출 수가 없어서 편법으로 @SpringBootApplication 이 지정된 bean이 생성된 직후 시점을 @R2dbcMapper bean을 생성하는 시점으로 잡았다. 

## 5. R2dbc Driver test notes

### MariaDB

- 테스트 서버가 같은 시간대로 설정되어 있어서 java.util.Date 가 정상적으로 타임존 정보가 유지되는지 테스트 해보지 못함.
- ZonedDateTime 을 파라메터로 전달하면 r2dbc 드라이버가 codec이 없다고 한다. (미지원으로 보임.)

### MySQL

- java.util.Date 타입의 파라메터를 전달하면 클라이언트의 타임존 정보가 무시되고 DB 서버의 시간대로 인식된다.
- DB로부터 전달된 DATETIME 값을 java.util.Date로 받으면 DB서버의 타임존 시간이라고 보고 로컬 시간대로 변환되어 받는다.
- ZonedDateTime 타입을 사용하면 타임존 정보가 정상적으로 유지된다.

### Oracle

- Oracle용 r2dbc 드라이버의 공식 문서상으로는 Oracle 18 이후 버전에서 공식 지원한다고 한다.
- Tomcat에 WAR로 배포하는 경우 Oracle JDBC 드라이버를 못찾는 에러가 발생한다. Jar 로 실행하는 경우에는 잘된다. (원인 불명)
- java.util.Date 타입의 값을 파라메터로 전달하거나 DB의 시간값을 읽어와서 java.util.Date 타입으로 받거나 모두 타임존 정보를 무시하고 각자의 로컬 타임으로 해석해서 사용한다.
