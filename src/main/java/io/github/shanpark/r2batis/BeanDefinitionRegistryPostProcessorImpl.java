package io.github.shanpark.r2batis;

import io.github.shanpark.r2batis.annotation.R2dbcMapper;
import io.github.shanpark.r2batis.sql.Mapper;
import io.github.shanpark.r2batis.sql.Query;
import io.github.shanpark.r2batis.sql.XmlMapperParser;
import org.apache.tools.ant.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
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

@Component
public class BeanDefinitionRegistryPostProcessorImpl implements BeanDefinitionRegistryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(BeanDefinitionRegistryPostProcessorImpl.class);

    private final Environment environment;
    private final ApplicationContext applicationContext;

    public BeanDefinitionRegistryPostProcessorImpl(Environment environment, ApplicationContext applicationContext) {
        this.environment = environment;
        this.applicationContext = applicationContext;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 여기서 beanFactory 인자는 singleton으로 앞으로도 불변이어야 한다.
        Map<String, InterfaceImpl> mapperMap = scanMapperXml();
        List<Class<?>> mapperInterfaceClasses = scanMapperInterface(mapperMap);
        for (Class<?> interfaceClass : mapperInterfaceClasses) {
            createR2dbcBean(beanFactory, mapperMap, interfaceClass);
        }
    }

    @Override
    @SuppressWarnings("NullableProblems")
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
        String mapperLocations = environment.getProperty("r2batis.mapper-locations");
        if (mapperLocations == null)
            mapperLocations = "classpath:mapper/**/*.xml";

        boolean mapperFound = false;
        Map<String, InterfaceImpl> mapperMap = new HashMap<>();
        String[] mapperPathPatterns = mapperLocations.split("\\s*,\\s*");
        for (String mapperPathPattern : mapperPathPatterns) {
            if (mapperPathPattern.startsWith("classpath:"))
                mapperFound = mapperFound || scanMapperXmlInResources(mapperMap, mapperPathPattern);
            else
                mapperFound = mapperFound || scanMapperXmlInDir(mapperMap, mapperPathPattern);
        }

        if (!mapperFound)
            log.warn("No mapper xml file was found.");

        return mapperMap;
    }

    private boolean scanMapperXmlInResources(Map<String, InterfaceImpl> mapperMap, String mapperPath) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(mapperPath);
            for (Resource resource : resources)
                generateInterfaceFromMapperXml(mapperMap, resource.getInputStream());

            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean scanMapperXmlInDir(Map<String, InterfaceImpl> mapperMap, String mapperPathPattern) {
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
                    generateInterfaceFromMapperXml(mapperMap, new FileInputStream(file));
                return true;
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateInterfaceFromMapperXml(Map<String, InterfaceImpl> mapperMap, InputStream inputStream) {
        Mapper mapper = XmlMapperParser.parse(inputStream);
        if (mapper != null) {
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
            mapperMap.putIfAbsent(interfaceImpl.getName(), interfaceImpl); // 먼저 들어간 게 우선순위가 높다.
        }
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
            try {
                URL packageUrl = R2batisAutoConfiguration.class.getClassLoader().getResource(packagePath);
                if (packageUrl != null) {
                    URI packageUri = packageUrl.toURI(); // URI로 변환해서 사용하는 것이 안전하다. URL은 몇몇 문제가 있다.
                    if (packageUri.getScheme().equals("jar")) {
                        mapperInterfaceList.addAll(scanMapperInterfaceInJar(mapperMap, packageUri));
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
                                mapperInterfaceList.addAll(scanMapperInterfaceInDir(mapperMap, packageName, packageDir));
                        }
                        // else // URI에서 path가 나오지 않으면 디렉토리는 아니므로 무시한다.
                    }
                } else {
                    throw new RuntimeException(String.format("There is no package[%s].", packageName)); // AutoConfigurationPackages 가 반환한 package가 존재하지 않는다는 뜻이다. 심각한 에러이므로 그냥 던진다.
                }
            } catch (URISyntaxException e) {
                throw new RuntimeException(e); // AutoConfigurationPackages 가 반환한 package가 존재하지 않는다는 뜻이다. 심각한 에러이므로 그냥 던진다.
            }
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
                    } catch (NoClassDefFoundError | ClassNotFoundException e) {
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

    private List<Class<?>> scanMapperInterfaceInJar(Map<String, InterfaceImpl> mapperMap, URI jarUri) throws URISyntaxException {
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
                            if (clazz.isAnnotationPresent(R2dbcMapper.class)) {
                                if (mapperMap.get(clazz.getName()) != null) {
                                    mapperInterfaceList.add(clazz);
                                }
                            }
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
