package io.github.shanpark.r2batis.core;

import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import io.r2dbc.spi.ConnectionFactory;
import lombok.Data;
import org.springframework.r2dbc.core.DatabaseClient;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Data
public class InterfaceImpl {
    private final String name;
    private final String connectionFactoryName;
    private final Map<String, MethodImpl> methodMap;

    private ConnectionFactory connectionFactory;
    private DatabaseClient databaseClient;

    public InterfaceImpl(String name, String connectionFactoryName) {
        this.name = name;
        this.connectionFactoryName = connectionFactoryName;
        methodMap = new HashMap<>();
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        databaseClient = DatabaseClient.builder().connectionFactory(connectionFactory).build();
    }

    public void addMethod(MethodImpl methodImpl) {
        if (methodMap.put(methodImpl.getName(), methodImpl) != null)
            throw new InvalidMapperElementException("Two or more query definitions were found. [" + name + "." + methodImpl.getName() + "]");
    }

    /**
     *
     * @param method 호출된 메소드에 대한 정보 객체.
     * @param args 실제 메소드 호출 시 전달된 argument 들의 배열. 메소드의 parameter가 없는 경우 null이 전달.
     * @return 호출된 메서드가 반환한 값.
     */
    public Object invoke(Method method, Object[] args) {
        MethodImpl methodImpl = methodMap.get(method.getName());
        if (methodImpl != null)
            return methodImpl.invoke(databaseClient, method, args);
        else
            throw new InvalidMapperElementException("There is no valid method with name [" + method.getName() + "]. Verify the method name or 'id' in the mapper XML");
    }
}
