package io.github.shanpark.r2batis;

import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import lombok.Data;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Data
public class InterfaceImpl {
    private final String name;
    private final Map<String, MethodImpl> methodMap;

    // 아래 두 객체는 모두 Bean 이지만 초기화 타이밍이 늦어서 생성자에서 초기화하지 못하고 늦게 생성해서 사용해야 한다.
    private volatile DatabaseClient databaseClient; // 한 번 얻은 bean을 재사용하기 위해서 캐슁함. 캐슁 기준이 되는 변수 이므로 volatile 선언.
    private TransactionalOperator transactionalOperator; // databaseClient과 life cycle이 같다.

    public InterfaceImpl(String name) {
        this.name = name;
        methodMap = new HashMap<>();
    }

    public void addMethod(MethodImpl methodImpl) {
        methodMap.put(methodImpl.getName(), methodImpl);
    }

    /**
     *
     * @param beanFactory spring에서 제공하는 bean factory 객체.
     * @param method 호출된 메소드에 대한 정보 객체.
     * @param args 실제 메소드 호출 시 전달된 argument 들의 배열. 메소드의 parameter가 없는 경우 null이 전달.
     * @return 호출된 메서드가 반환한 값.
     */
    public Object invoke(ConfigurableListableBeanFactory beanFactory, Method method, Object[] args) {
        if (databaseClient == null) {
            synchronized (this) {
                // "r2dbcDatabaseClient" Bean을 얻어야 DatabaseClient, TransactionalOperator bean을 생성할 수 있는데
                // BeanDefinitionRegistryPostProcessorImpl.postProcessBeanFactory() 에서는 r2dbc의 url을 application.yml에서
                // 가져오지 못하여 "r2dbcDatabaseClient" Bean을 생성 중 실패하게 된다.
                // 따라서 Autowired 등의 방법으로 databaseClient를 받을 수가 없고 이렇게 최초 메소드 호출될 때 초기화하는 방법을 택하였다.
                if (databaseClient == null) {
                    databaseClient = beanFactory.getBean(DatabaseClient.class);
                    transactionalOperator = beanFactory.getBean(TransactionalOperator.class);
                }
            }
        }

        MethodImpl methodImpl = methodMap.get(method.getName());
        if (methodImpl != null)
            return methodImpl.invoke(databaseClient, transactionalOperator, method, args);
        else
            throw new InvalidMapperElementException("There is no valid method with name [" + method.getName() + "]. Verify the method name or 'id' in the mapper XML");
    }
}
