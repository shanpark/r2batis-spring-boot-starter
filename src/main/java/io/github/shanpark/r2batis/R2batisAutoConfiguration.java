package io.github.shanpark.r2batis;

import io.github.shanpark.r2batis.annotation.R2dbcMapper;
import io.github.shanpark.r2batis.sql.Mapper;
import io.github.shanpark.r2batis.sql.Query;
import io.github.shanpark.r2batis.sql.XmlMapperParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

@Slf4j
@AutoConfiguration
public class R2batisAutoConfiguration implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware {

    private Environment environment;
    private ApplicationContext applicationContext;

    @Override
    public void setEnvironment(Environment environment) {
        log.trace("R2batisAutoConfiguration class loaded.");
        this.environment = environment;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 여기서 beanFactory 인자는 singleton으로 앞으로도 불변이어야 한다.
        Map<String, InterfaceImpl> mapperMap = scanMapperXml();
        List<Class<?>> mapperInterfaceClasses = scanMapperInterface(mapperMap);
        for (Class<?> interfaceClass : mapperInterfaceClasses) {
            createR2dbcBean(beanFactory, mapperMap, interfaceClass);
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // do nothing
    }

    /**
     * 지정된 경로에서 mapper xml 파일을 찾아서 InterfaceImpl 객체를 생성해서
     * map에 담아서 반환한다.
     *
     * @return 생성된 InterfaceImpl 객체가 담겨 있는 Map 객체.
     */
    private Map<String, InterfaceImpl> scanMapperXml() {
        String mapperPath = environment.getProperty("r2dbc.mapper-locations");
        if (mapperPath == null)
            mapperPath = "./**/*.xml";
        Map<String, InterfaceImpl> mapperMap = new HashMap<>();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:" + mapperPath);
            for (Resource resource : resources) {
                InputStream inputStream = resource.getInputStream();

                Mapper mapper = XmlMapperParser.parse(inputStream);
                if (mapper == null)
                    continue;
                InterfaceImpl interfaceImpl = new InterfaceImpl(mapper.getInterfaceName());

                if (!CollectionUtils.isEmpty(mapper.getSelectList())) { // null 체크도 해줌.
                    for (Query query : mapper.getSelectList())
                        interfaceImpl.addMethod(new MethodImpl(query.getId(), query));
                }
                if (!CollectionUtils.isEmpty(mapper.getInsertList())) {
                    for (Query query : mapper.getInsertList())
                        interfaceImpl.addMethod(new MethodImpl(query.getId(), query));
                }
                if (!CollectionUtils.isEmpty(mapper.getUpdateList())) {
                    for (Query query : mapper.getUpdateList())
                        interfaceImpl.addMethod(new MethodImpl(query.getId(), query));
                }
                if (!CollectionUtils.isEmpty(mapper.getDeleteList())) {
                    for (Query query : mapper.getDeleteList())
                        interfaceImpl.addMethod(new MethodImpl(query.getId(), query));
                }
                mapperMap.put(interfaceImpl.getName(), interfaceImpl);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return mapperMap;
    }

    /**
     * {@code @R2dbcMapper} 어노테이션이 붙어있는 인터페이스를 찾는다.
     *
     * @param mapperMap InterfaceImpl 객체가 담겨 있는 Map 객체.
     * @return @R2dbcMapper 어노테이션이 붙어있는 인터페이스의 Class 객체 리스트 반환.
     */
    private List<Class<?>> scanMapperInterface(Map<String, InterfaceImpl> mapperMap) {
        List<String> autoConfigurationPackages = AutoConfigurationPackages.get(applicationContext.getAutowireCapableBeanFactory());

        List<Class<?>> mapperInterfaceList = new ArrayList<>();
        for (String packageName : autoConfigurationPackages) {
            String packagePath = packageName.replace(".", "/");
            String packageDirectoryPath = Objects.requireNonNull(R2batisAutoConfiguration.class.getClassLoader().getResource(packagePath)).getFile();

            File packageDirectory = new File(packageDirectoryPath);
            if (packageDirectory.isDirectory())
                mapperInterfaceList.addAll(scanMapperInterfaceInDir(mapperMap, packageName, packageDirectory));
        }

        return mapperInterfaceList;
    }

    private List<Class<?>> scanMapperInterfaceInDir(Map<String, InterfaceImpl> mapperMap, String packageName, File dir) {
        List<Class<?>> mapperInterfaceList = new ArrayList<>();

        String[] files = dir.list();
        if (files != null) {
            for (String fileName : files) {
                if (fileName.endsWith(".class")) {
                    try {
                        String className = fileName.substring(0, fileName.length() - 6);
                        Class<?> clazz = Class.forName(packageName + "." + className);
                        if (clazz.isInterface()) {
                            if (clazz.isAnnotationPresent(R2dbcMapper.class)) {
                                if (mapperMap.get(clazz.getName()) != null) {
                                    mapperInterfaceList.add(clazz);
                                }
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        // just ignore this class..
                    }
                } else {
                    File file = new File(dir, fileName);
                    if (file.isDirectory()) {
                        mapperInterfaceList.addAll(scanMapperInterfaceInDir(mapperMap, packageName + "." + fileName, file));
                    }
                }
            }
        }

        return mapperInterfaceList;
    }

    /**
     *
     * @param beanFactory 스프링 부트가 제공하는 bean factory 객체.
     * @param mapperMap xml 파일로부터 생성된 InterfaceImpl 객체가 담겨있는 Map 객체.
     * @param interfaceClass 생성할 Bean이 구현할 interface의 Class 객체.
     */
    private void createR2dbcBean(ConfigurableListableBeanFactory beanFactory, Map<String, InterfaceImpl> mapperMap, Class<?> interfaceClass) {
        // 인터페이스의 구현체를 생성.
        Object bean = Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[] { interfaceClass },
                new InvocationHandler() {
                    private final InterfaceImpl interfaceImpl = mapperMap.get(interfaceClass.getName());

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        return interfaceImpl.invoke(beanFactory, method, args);
                    }
                }
        );
        beanFactory.registerSingleton(interfaceClass.getSimpleName(), bean);
    }
}
