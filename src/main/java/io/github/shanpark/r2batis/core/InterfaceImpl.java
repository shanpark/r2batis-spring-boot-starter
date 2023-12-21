package io.github.shanpark.r2batis.core;

import io.github.shanpark.r2batis.R2batisAutoConfiguration;
import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import io.github.shanpark.r2batis.mapper.Mapper;
import io.github.shanpark.r2batis.mapper.Query;
import io.github.shanpark.r2batis.mapper.XmlMapperParser;
import io.r2dbc.spi.ConnectionFactory;
import lombok.Getter;
import org.apache.tools.ant.DirectoryScanner;
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

    public void initialize(ApplicationContext applicationContext) {
        ConnectionFactory connFactory;
        if (connectionFactoryName.isBlank())
            connFactory = applicationContext.getBean(ConnectionFactory.class);
        else
            connFactory = (ConnectionFactory) applicationContext.getBean(connectionFactoryName);
        connectionFactory = connFactory;
        databaseClient = DatabaseClient.builder().connectionFactory(connFactory).build();

        R2batisProperties r2batisProps;
        if (r2batisPropertiesName.isBlank())
            r2batisProps = R2batisAutoConfiguration.defaultR2batisProperties; // default R2batisProperties 값으로 초기화 한다.
        else
            r2batisProps = (R2batisProperties) applicationContext.getBean(r2batisPropertiesName);
        r2batisProperties = r2batisProps;

        scanMapperXml(applicationContext);
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
    private void scanMapperXml(ApplicationContext applicationContext) {
        String mapperLocations = r2batisProperties.getMapperLocations();
        if (mapperLocations == null)
            mapperLocations = "classpath:mapper/**/*.xml"; // default location

        String[] mapperPathPatterns = mapperLocations.split("\\s*,\\s*");
        for (String mapperPathPattern : mapperPathPatterns) {
            if (mapperPathPattern.startsWith("classpath:"))
                scanMapperXmlInResources(applicationContext, mapperPathPattern);
            else
                scanMapperXmlInDir(applicationContext, mapperPathPattern);
        }
    }

    private void scanMapperXmlInResources(ApplicationContext applicationContext, String mapperPath) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(mapperPath);
            for (Resource resource : resources)
                generateInterfaceFromMapperXml(applicationContext, resource.getInputStream());

        } catch (FileNotFoundException ignored) {
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void scanMapperXmlInDir(ApplicationContext applicationContext, String mapperPathPattern) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setIncludes(new String[]{ mapperPathPattern });
        if (!mapperPathPattern.startsWith(File.separator))
            scanner.setBasedir("."); // current dir.
        scanner.scan();

        try {
            String[] files = scanner.getIncludedFiles();
            for (String file : files)
                generateInterfaceFromMapperXml(applicationContext, new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateInterfaceFromMapperXml(ApplicationContext applicationContext, InputStream inputStream) {
        Mapper mapper = XmlMapperParser.parse(inputStream);
        if (mapper != null) {
            if (!mapper.getInterfaceName().equals(name))
                return; // TODO 매번 parse를 하지 않고 한 번만 parse하면 다시 캐슁해서 사용할 수 있을 것 같은디...

            DatabaseIdProvider databaseIdProvider = applicationContext.getBean(DatabaseIdProvider.class);
            String databaseId = databaseIdProvider.getDatabaseId(connectionFactory);

            if (!CollectionUtils.isEmpty(mapper.getSelectList())) { // null 체크도 해줌.
                for (Query query : mapper.getSelectList()) {
                    if (databaseId == null || query.getDatabaseId().isBlank() || Objects.equals(databaseId, query.getDatabaseId()))
                        addMethod(new MethodImpl(this, query.getId(), query));
                }
            }
            if (!CollectionUtils.isEmpty(mapper.getInsertList())) {
                for (Query query : mapper.getInsertList()) {
                    if (databaseId == null || query.getDatabaseId().isBlank() || Objects.equals(databaseId, query.getDatabaseId()))
                        addMethod(new MethodImpl(this, query.getId(), query));
                }
            }
            if (!CollectionUtils.isEmpty(mapper.getUpdateList())) {
                for (Query query : mapper.getUpdateList()) {
                    if (databaseId == null || query.getDatabaseId().isBlank() || Objects.equals(databaseId, query.getDatabaseId()))
                        addMethod(new MethodImpl(this, query.getId(), query));
                }
            }
            if (!CollectionUtils.isEmpty(mapper.getDeleteList())) {
                for (Query query : mapper.getDeleteList()) {
                    if (databaseId == null || query.getDatabaseId().isBlank() || Objects.equals(databaseId, query.getDatabaseId()))
                        addMethod(new MethodImpl(this, query.getId(), query));
                }
            }
        }
    }
}
