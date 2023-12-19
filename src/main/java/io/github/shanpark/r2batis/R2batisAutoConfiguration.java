package io.github.shanpark.r2batis;

import io.github.shanpark.r2batis.core.R2batisInitializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
public class R2batisAutoConfiguration {

    public static boolean isTesting = false;
    public static boolean mapUnderscoreToCamelCase = false;

    @Bean
    public BeanDefinitionRegistryPostProcessor beanDefinitionRegistryPostProcessor(ApplicationContext applicationContext) {
        return new BeanDefinitionRegistryPostProcessorImpl(applicationContext);
    }

    @Bean
    public R2batisInitializer r2BatisInitializer(ApplicationContext applicationContext) {
        return new R2batisInitializer(applicationContext);
    }
}
