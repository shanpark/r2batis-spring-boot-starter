package io.github.shanpark.r2batis.core;

import io.github.shanpark.r2batis.mapper.Mapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

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
        Map<String, Mapper> mapperXmlCache = new HashMap<>(); // 로컬에서 캐쉬 버퍼로 사용되고 버린다. 초기화 완료 후 버린다.
        interfaceMap.values().forEach(interfaceImpl -> interfaceImpl.initialize(applicationContext, mapperXmlCache));
    }
}
