package io.github.shanpark.r2batis.configure;

import io.github.shanpark.r2batis.annotation.R2dbcMapper;
import io.github.shanpark.r2batis.core.InterfaceImpl;
import io.github.shanpark.r2batis.core.R2batisProperties;
import io.github.shanpark.r2batis.mapper.Mapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class R2batisBeanPostProcessor implements BeanPostProcessor {

    private final ApplicationContext applicationContext;

    public R2batisBeanPostProcessor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean.getClass().isAnnotationPresent(SpringBootApplication.class)) {
            if (applicationContext instanceof ConfigurableApplicationContext) {
                createDefaultR2batisProperties();

                // 여기서는 @R2batisMapper 가 지정된 interface만 찾아서 bean으로 일단 등록해준다.
                Map<String, Mapper> mapperXmlCache = new HashMap<>(); // 로컬에서 캐쉬 버퍼로 사용되고 버린다. 초기화 완료 후 버린다.
                List<Class<?>> mapperInterfaceClasses = scanMapperInterface();
                for (Class<?> interfaceClass : mapperInterfaceClasses)
                    createR2dbcBean(((ConfigurableApplicationContext) applicationContext).getBeanFactory(), interfaceClass, mapperXmlCache);
            }
        }
        return bean;
    }

    /**
     * default 값을 갖는 R2batisProperties 객체를 하나 생성해둔다. (Bean 아님)
     * 아무 설정도 하지 않으면 아래 값들이 default가 된다.
     * - r2batis.mapper-locations = "classpath:mapper\/**\/*.xml"
     * - r2batis.configuration.mapUnderscoreToCamelCase = false
     * application.properties에 위 값을 설정하면 그 값이 override 한다.
     */
    private void createDefaultR2batisProperties() {
        String mapperLocations = applicationContext.getEnvironment().getProperty("r2batis.mapper-locations");
        String mapUnderscoreToCamelCaseStr = applicationContext.getEnvironment().getProperty("r2batis.configuration.mapUnderscoreToCamelCase");
        boolean mapUnderscoreToCamelCase = false;

        if (mapperLocations == null || mapperLocations.isBlank())
            mapperLocations = "classpath:mapper/**/*.xml";
        if (mapUnderscoreToCamelCaseStr != null && !mapUnderscoreToCamelCaseStr.isBlank())
            mapUnderscoreToCamelCase = Boolean.parseBoolean(mapUnderscoreToCamelCaseStr);

        // Bean을 등록하지 않고 R2batisAutoConfiguration.r2batisProperties에 저장해둔다.
        R2batisAutoConfiguration.defaultR2batisProperties = new R2batisProperties(mapperLocations, mapUnderscoreToCamelCase);
    }


    /**
     * {@code @R2dbcMapper} 어노테이션이 붙어있는 인터페이스를 찾는다.
     *
     * @return @R2dbcMapper 어노테이션이 붙어있는 인터페이스의 Class 객체 리스트 반환.
     */
    private List<Class<?>> scanMapperInterface() {
        List<String> autoConfigurationPackages = AutoConfigurationPackages.get(applicationContext.getAutowireCapableBeanFactory());
        List<Class<?>> mapperInterfaceList = new ArrayList<>();
        for (String packageName : autoConfigurationPackages) {
            String packagePath = packageName.replace(".", "/");
            try {
                URL packageUrl = R2batisAutoConfiguration.class.getClassLoader().getResource(packagePath);
                if (packageUrl != null) {
                    URI packageUri = packageUrl.toURI(); // URI로 변환해서 사용하는 것이 안전하다. URL은 몇몇 문제가 있다.
                    if (packageUri.getScheme().equals("jar")) {
                        mapperInterfaceList.addAll(scanMapperInterfaceInJar(packageUri));
                    } else {
                        String packageDirectoryPath = packageUri.getPath();

                        //>>> for Gradle Test
                        // test 클래스의 생성자에서 isTesting을 true로 변경해주면 동작한다.
                        if (R2batisAutoConfiguration.isTesting && packageDirectoryPath.contains("/classes/java/test/")) // test시 경로 확인 방법은?
                            packageDirectoryPath = packageDirectoryPath.replace("/classes/java/test/", "/classes/java/main/");
                        //<<< for Gradle Test

                        if (packageDirectoryPath != null) {
                            File packageDir = new File(packageDirectoryPath);
                            if (packageDir.isDirectory())
                                mapperInterfaceList.addAll(scanMapperInterfaceInDir(packageName, packageDir));
                        }
                        // else // URI에서 path가 나오지 않으면 디렉토리는 아니므로 무시한다.
                    }
                } else {
                    throw new RuntimeException(String.format("There is no package[%s].", packageName)); // AutoConfigurationPackages 가 반환한 package가 존재하지 않는다는 뜻이다. 심각한 에러이므로 그냥 던진다.
                }
            } catch (URISyntaxException e) {
                throw new RuntimeException(e); // 심각한 에러이므로 그냥 던진다.
            }
        }

        return mapperInterfaceList;
    }

    private List<Class<?>> scanMapperInterfaceInDir(String packageName, File dir) {
        List<Class<?>> mapperInterfaceList = new ArrayList<>();

        String[] files = dir.list();
        if (files != null) {
            for (String fileName : files) {
                if (fileName.endsWith(".class")) {
                    try {
                        String className = fileName.substring(0, fileName.length() - 6);
                        Class<?> clazz = Class.forName(packageName + "." + className);
                        if (clazz.isInterface()) {
                            if (clazz.isAnnotationPresent(R2dbcMapper.class))
                                mapperInterfaceList.add(clazz);
                        }
                    } catch (NoClassDefFoundError | ClassNotFoundException e) {
                        // just ignore this class..
                    }
                } else {
                    File file = new File(dir, fileName);
                    if (file.isDirectory()) {
                        mapperInterfaceList.addAll(scanMapperInterfaceInDir(packageName + "." + fileName, file));
                    }
                }
            }
        }

        return mapperInterfaceList;
    }

    private List<Class<?>> scanMapperInterfaceInJar(URI jarUri) throws URISyntaxException {
        String schemeSpecificPart = jarUri.getSchemeSpecificPart(); // 여기까지 왔으면 jarUri 파라메터는 jar:~~~ 형식의 URI 이다.
        String jarPath = schemeSpecificPart.substring(0, schemeSpecificPart.indexOf("!")); // jar:file:~~~!~~~ 에서 file:~~~ 부분을 떼어낸다.
        jarUri = new URI(jarPath); // file:~~~ 로 URI 재생성.

        List<Class<?>> mapperInterfaceList = new ArrayList<>();
        try {
            JarFile jarFile = new JarFile(new File(jarUri)); // if not jar file, will throw IOException.

            List<String> ignorePrefixes = getClassPathesFromManifest(jarFile);

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (!entry.isDirectory() && entryName.endsWith(".class")) {
                    String className = getClassNameFromJarEntry(entryName, ignorePrefixes);
                    try {
                        Class<?> clazz = Class.forName(className);
                        if (clazz.isInterface()) {
                            if (clazz.isAnnotationPresent(R2dbcMapper.class))
                                mapperInterfaceList.add(clazz);
                        }
                    } catch (NoClassDefFoundError | ClassNotFoundException e) {
                        // there is no such class. ignore.
                    }
                }
            }

            jarFile.close();
        } catch (IOException e) {
            // not a jar file. ignore.
        }

        return mapperInterfaceList;
    }

    /**
     * Jar 파일의 Manifest에 선언된 class path 들을 가져온다.
     * @param jarFile JarFile 객체.
     * @return 수집된 class path List 객체
     */
    private List<String> getClassPathesFromManifest(JarFile jarFile) {
        try {
            Manifest manifest = jarFile.getManifest();
            return Stream.of("Class-Path", "Spring-Boot-Classes") // Manifest에 class path를 지정하는 키값들이다. 이보다 더 있을 수 있다.
                    .map(classpathKey -> manifest.getMainAttributes().getValue(classpathKey))
                    .filter(classPath -> classPath != null && !classPath.isBlank())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Jar 파일의 entry name으로부터 class의 full name을 가져온다.
     * @param entryName Jar 파일의 entry name.
     * @param ignorePrefixes class path 경로들을 갖는 List 객체.
     * @return 최종적으로 변환된 class 의 full name.
     */
    private String getClassNameFromJarEntry(String entryName, List<String> ignorePrefixes) {
        for (String ignorePrefix : ignorePrefixes) {
            if (entryName.startsWith(ignorePrefix)) {
                entryName = entryName.substring(ignorePrefix.length()); // entry는 전체 경로를 가지고 있으므로 앞에 붙은 class path 경로는 떼낸다.
                break;
            }
        }

        return entryName.replace('/', '.').substring(0, entryName.length() - 6);
    }

    /**
     * interfaceClass 를 구현하는 proxy instance를 하나 생성하고 beanFactory에 등록해준다.
     *
     * @param beanFactory    스프링 부트가 제공하는 bean factory 객체.
     * @param interfaceClass 생성할 Bean이 구현할 interface의 Class 객체.
     * @param mapperXmlCache xml을 2번 parsing할 필요가 없기 때문에 이미 parsing된 Mapper 객체를 보관하여 재사용하기 위한 버퍼이다.
     */
    private void createR2dbcBean(ConfigurableListableBeanFactory beanFactory, Class<?> interfaceClass, Map<String, Mapper> mapperXmlCache) {
        R2dbcMapper r2dbcAnnotation = interfaceClass.getAnnotation(R2dbcMapper.class);
        String connectionFactoryName = r2dbcAnnotation.connectionFactory();
        String r2batisPropertiesName = r2dbcAnnotation.r2batisProperties();

        InterfaceImpl interfaceImpl = new InterfaceImpl(interfaceClass.getName(), connectionFactoryName, r2batisPropertiesName);
        interfaceImpl.initialize(applicationContext, mapperXmlCache);

        Object bean = Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                (proxy, method, args) -> interfaceImpl.invoke(method, args)
        );
        beanFactory.registerSingleton(interfaceClass.getSimpleName(), bean);
    }
}
