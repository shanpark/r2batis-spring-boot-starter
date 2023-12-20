package io.github.shanpark.r2batis.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class MapperInvocationHandler implements InvocationHandler {

    private final InterfaceImpl interfaceImpl;

    public MapperInvocationHandler(String interfaceName, String connectionFactoryName) {
        interfaceImpl = new InterfaceImpl(interfaceName, connectionFactoryName);
        R2batisInitializer.interfaceMap.put(interfaceName, interfaceImpl);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        return interfaceImpl.invoke(method, args);
    }
}
