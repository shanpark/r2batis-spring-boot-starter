package io.github.shanpark.r2batis.core;

import io.github.shanpark.r2batis.R2batisAutoConfiguration;
import io.github.shanpark.r2batis.mapper.Mapper;
import io.github.shanpark.r2batis.mapper.Query;
import io.github.shanpark.r2batis.mapper.XmlMapperParser;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.tools.ant.DirectoryScanner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * R2Batis용 XML 맵퍼 파일들을 읽어들이고 초기화 한다.
 * BeanDefinitionRegistryPostProcessorImpl bean에서는 ConnectionFactory bean 등이 등록되지 않은 상태이기 때문에
 * 초기화를 완료할 수 없다. 따라서 BeanDefinitionRegistryPostProcessorImpl의 초기화 부분에서는 @R2dbcMapper 인터페이스만
 * 스캔하여 동작하지 않는 Bean을 만들어서 등록만 해준다. 이후 R2BatisInitializer bean이 등록되었을 때는 ConnectionFactory 등의
 * bean이 모두 사용가능한 상태가 되기 때문에 R2BatisInitializer의 postConstruct() 메소드에서 XML을 읽어들이고 초기화를
 * 완료하도록 구현되었다. 이 후 부터 R2dbcMapper 인터페이스의 메소드 호출이 정상동작하게 된다.
 */
@Slf4j
public class R2batisInitializer {
    static final Map<String, InterfaceImpl> interfaceMap = new HashMap<>();

    private final ApplicationContext applicationContext;

    public R2batisInitializer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    private void initialize() {
        R2batisAutoConfiguration.mapUnderscoreToCamelCase = Boolean.parseBoolean(applicationContext.getEnvironment().getProperty("r2batis.configuration.mapUnderscoreToCamelCase"));

        interfaceMap.values().forEach(interfaceImpl -> {
            ConnectionFactory connectionFactory;
            if (interfaceImpl.getConnectionFactoryName().isBlank())
                connectionFactory = applicationContext.getBean(ConnectionFactory.class);
            else
                connectionFactory = (ConnectionFactory) applicationContext.getBean(interfaceImpl.getConnectionFactoryName());
            interfaceImpl.setConnectionFactory(connectionFactory);
        });

        scanMapperXml();
    }

    /**
     * 지정된 경로에서 mapper xml 파일을 찾아서 분석 후 MethodImpl 객체를 생성하여
     * interfaceMap에 등록된 InterfaceImpl 객체에 추가해준다.
     */
    private void scanMapperXml() {
        String mapperLocations = applicationContext.getEnvironment().getProperty("r2batis.mapper-locations");
        if (mapperLocations == null)
            mapperLocations = "classpath:mapper/**/*.xml"; // default location

        boolean mapperFound = false;
        String[] mapperPathPatterns = mapperLocations.split("\\s*,\\s*");
        for (String mapperPathPattern : mapperPathPatterns) {
            if (mapperPathPattern.startsWith("classpath:"))
                mapperFound = mapperFound || scanMapperXmlInResources(mapperPathPattern);
            else
                mapperFound = mapperFound || scanMapperXmlInDir(mapperPathPattern);
        }

        if (!mapperFound)
            log.warn("No mapper xml file was found.");
    }

    private boolean scanMapperXmlInResources(String mapperPath) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(mapperPath);
            for (Resource resource : resources)
                generateInterfaceFromMapperXml(resource.getInputStream());

            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean scanMapperXmlInDir(String mapperPathPattern) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setIncludes(new String[]{ mapperPathPattern });
        if (!mapperPathPattern.startsWith(File.separator))
            scanner.setBasedir("."); // current dir.
        scanner.scan();

        try {
            String[] files = scanner.getIncludedFiles();
            if (files.length == 0) {
                return false;
            } else {
                for (String file : files)
                    generateInterfaceFromMapperXml(new FileInputStream(file));
                return true;
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateInterfaceFromMapperXml(InputStream inputStream) {

        Mapper mapper = XmlMapperParser.parse(inputStream);
        if (mapper != null) {
            InterfaceImpl interfaceImpl = R2batisInitializer.interfaceMap.get(mapper.getInterfaceName());

            DatabaseIdProvider databaseIdProvider = applicationContext.getBean(DatabaseIdProvider.class);
            String databaseId = databaseIdProvider.getDatabaseId(interfaceImpl.getConnectionFactory());

            if (!CollectionUtils.isEmpty(mapper.getSelectList())) { // null 체크도 해줌.
                for (Query query : mapper.getSelectList()) {
                    if (databaseId == null || query.getDatabaseId().isBlank() || Objects.equals(databaseId, query.getDatabaseId()))
                        interfaceImpl.addMethod(new MethodImpl(query.getId(), query));
                }
            }
            if (!CollectionUtils.isEmpty(mapper.getInsertList())) {
                for (Query query : mapper.getInsertList()) {
                    if (databaseId == null || query.getDatabaseId().isBlank() || Objects.equals(databaseId, query.getDatabaseId()))
                        interfaceImpl.addMethod(new MethodImpl(query.getId(), query));
                }
            }
            if (!CollectionUtils.isEmpty(mapper.getUpdateList())) {
                for (Query query : mapper.getUpdateList()) {
                    if (databaseId == null || query.getDatabaseId().isBlank() || Objects.equals(databaseId, query.getDatabaseId()))
                        interfaceImpl.addMethod(new MethodImpl(query.getId(), query));
                }
            }
            if (!CollectionUtils.isEmpty(mapper.getDeleteList())) {
                for (Query query : mapper.getDeleteList()) {
                    if (databaseId == null || query.getDatabaseId().isBlank() || Objects.equals(databaseId, query.getDatabaseId()))
                        interfaceImpl.addMethod(new MethodImpl(query.getId(), query));
                }
            }
        }
    }
}
