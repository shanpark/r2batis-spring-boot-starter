package io.github.shanpark.r2batis.core;

import io.github.shanpark.r2batis.configure.R2batisAutoConfiguration;
import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import io.github.shanpark.r2batis.mapper.Mapper;
import io.github.shanpark.r2batis.mapper.Query;
import io.github.shanpark.r2batis.mapper.XmlMapperParser;
import io.r2dbc.spi.ConnectionFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tools.ant.DirectoryScanner;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class InterfaceImpl {
    private final String name;
    private final String connectionFactoryName;
    private final String r2batisPropertiesName;
    private final Map<String, MethodImpl> methodMap;

    private ConnectionFactory connectionFactory;
    private DatabaseClient databaseClient;
    @Getter
    private R2batisProperties r2batisProperties;

    public InterfaceImpl(String name, String connectionFactoryName, String r2batisPropertiesName) {
        this.name = name;
        this.connectionFactoryName = connectionFactoryName;
        this.r2batisPropertiesName = r2batisPropertiesName;
        methodMap = new HashMap<>();
    }

    public void initialize(ApplicationContext applicationContext, Map<String, Mapper> mapperXmlCache) {
        // mapper interface에서 사용될 connectionFactory를 찾아서 databaseClient 초기화.
        ConnectionFactory connFactory;
        try {
            if (connectionFactoryName.isBlank())
                connFactory = applicationContext.getBean(ConnectionFactory.class);
            else
                connFactory = (ConnectionFactory) applicationContext.getBean(connectionFactoryName);
            connectionFactory = connFactory;
            databaseClient = DatabaseClient.builder().connectionFactory(connFactory).build();
        } catch (NoUniqueBeanDefinitionException  e) {
            log.warn("Multiple connection factory for '{}' were found", name);
            return;
        } catch (NoSuchBeanDefinitionException e) {
            log.warn("No connection factory for '{}' was found", name);
            return;
        }

        // mapper interface에서 사용될 r2batisProperties 초기화.
        R2batisProperties r2batisProps;
        try {
            if (r2batisPropertiesName.isBlank())
                r2batisProps = R2batisAutoConfiguration.defaultR2batisProperties; // default R2batisProperties 값으로 초기화 한다.
            else
                r2batisProps = (R2batisProperties) applicationContext.getBean(r2batisPropertiesName);
        } catch (NoSuchBeanDefinitionException e) {
            log.warn("'{}' bean for '{}' was not found. The default properties will be used.", r2batisPropertiesName, name);
            r2batisProps = R2batisAutoConfiguration.defaultR2batisProperties; // default R2batisProperties 값으로 초기화 한다.
        }
        r2batisProperties = r2batisProps;

        // mapper xml 찾아서 초기화.
        scanMapperXml(applicationContext, mapperXmlCache);
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
            throw new InvalidMapperElementException("There is no valid method with name [" + method.getName() + "]. Verify the method name, 'id' or 'databaseId' in the mapper XML");
    }

    /**
     * 지정된 경로에서 mapper xml 파일을 찾아서 분석 후 MethodImpl 객체를 생성하여
     * interfaceMap에 등록된 InterfaceImpl 객체에 추가해준다.
     */
    private void scanMapperXml(ApplicationContext applicationContext, Map<String, Mapper> mapperXmlCache) {
        String mapperLocations = r2batisProperties.getMapperLocations();
        if (mapperLocations == null)
            mapperLocations = "classpath:mapper/**/*.xml"; // default location

        String[] mapperPathPatterns = mapperLocations.split("\\s*,\\s*");
        for (String mapperPathPattern : mapperPathPatterns) {
            if (mapperPathPattern.startsWith("classpath:"))
                scanMapperXmlInResources(applicationContext, mapperPathPattern, mapperXmlCache);
            else
                scanMapperXmlInDir(applicationContext, mapperPathPattern, mapperXmlCache);
        }
    }

    private void scanMapperXmlInResources(ApplicationContext applicationContext, String mapperPath, Map<String, Mapper> mapperXmlCache) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(mapperPath);
            for (Resource resource : resources) {
                Mapper mapper = mapperXmlCache.get(resource.getURI().toString());
                if (mapper != null) {
                    initializeMethodsFromMapperXml(applicationContext, mapper);
                } else {
                    mapper = initializeMethodsFromMapperXml(applicationContext, resource.getInputStream());
                    mapperXmlCache.put(resource.getURI().toString(), mapper); // 2번 parsing하지 않도록 cache에 저장.
                }
            }
        } catch (FileNotFoundException ignored) {
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void scanMapperXmlInDir(ApplicationContext applicationContext, String mapperPathPattern, Map<String, Mapper> mapperXmlCache) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setIncludes(new String[]{ mapperPathPattern });
        if (!mapperPathPattern.startsWith(File.separator))
            scanner.setBasedir("."); // current dir.
        scanner.scan();

        try {
            String[] files = scanner.getIncludedFiles();
            for (String file : files) {
                Mapper mapper = mapperXmlCache.get(file);
                if (mapper != null) {
                    initializeMethodsFromMapperXml(applicationContext, mapper);
                } else {
                    mapper = initializeMethodsFromMapperXml(applicationContext, new FileInputStream(file));
                    mapperXmlCache.put(file, mapper); // 2번 parsing하지 않도록 cache에 저장.
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Mapper initializeMethodsFromMapperXml(ApplicationContext applicationContext, InputStream inputStream) {
        Mapper mapper = XmlMapperParser.parse(inputStream);
        if (mapper == null)
            return new Mapper(); // empty mapper를 반환한다. 같은 리소스를 다시 parse하지 않도록 하기 위함이다.

        initializeMethodsFromMapperXml(applicationContext, mapper);
        return mapper;
    }

    private void initializeMethodsFromMapperXml(ApplicationContext applicationContext, Mapper mapper) {
        if (!mapper.getInterfaceName().equals(name))
            return;

        String databaseId;
        try {
            DatabaseIdProvider databaseIdProvider = applicationContext.getBean(DatabaseIdProvider.class);
            databaseId = databaseIdProvider.getDatabaseId(connectionFactory);
        } catch (NoSuchBeanDefinitionException e) {
            databaseId = null;
        }

        if (!CollectionUtils.isEmpty(mapper.getQueryList())) { // null 체크도 해줌.
            for (Query query : mapper.getQueryList()) {
                if (databaseId == null || query.getDatabaseId().isBlank() || Objects.equals(databaseId, query.getDatabaseId()))
                    addMethod(new MethodImpl(this, query.getId(), query));
            }
        }
    }
}
