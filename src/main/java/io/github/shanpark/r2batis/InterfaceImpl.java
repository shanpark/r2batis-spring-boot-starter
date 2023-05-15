package io.github.shanpark.r2batis;

import lombok.Data;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.r2dbc.core.DatabaseClient;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Data
public class InterfaceImpl {
    private final String name;
    private final Map<String, MethodImpl> methodMap;
    private DatabaseClient databaseClient;

    public InterfaceImpl(String name) {
        this.name = name;
        methodMap = new HashMap<>();
    }

    public void addMethod(MethodImpl methodImpl) {
        methodMap.put(methodImpl.getName(), methodImpl);
    }

    public Object invoke(ConfigurableListableBeanFactory beanFactory, Method method, Object[] args) {
        if (databaseClient == null)
            databaseClient = beanFactory.getBean(DatabaseClient.class);

        MethodImpl methodImpl = methodMap.get(method.getName());
        if (methodImpl != null)
            return methodImpl.invoke(databaseClient, method, args);
        else
            throw new RuntimeException("There is no valid method with name [" + method.getName() + "]. Verify the method name or 'id' in the mapper XML");
    }
}
