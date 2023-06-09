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
    implementation 'com.github.shanpark:r2batis-spring-boot-starter:0.0.9'
    // include vendor dependent R2DBC driver.
}
```

## 2. Configuration

### application.yml

```yaml
r2batis:
  mapper-locations: classpath:mapper/**/*.xml #, xxx, yyy, ...
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

  - The `<selectKey>` element can only be used within an `<insert>` element and can be used only once.
  - The `keyColumn` attribute of the `<selectKey>` element can only contain a single column name.
  - `<insert>`, `<update>`, `<delete>` elements return the number of affected rows.  
    And the result type is `Long`. (MySQL, MaraiDB implementation tested.)