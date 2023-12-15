package io.github.shanpark.r2batis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@Slf4j
@AutoConfiguration
public class R2batisAutoConfiguration {

    public static boolean isTesting = false;
    public static boolean mapUnderscoreToCamelCase = false;

    @Bean
    public static BeanDefinitionRegistryPostProcessor beanDefinitionRegistryPostProcessor(Environment environment, ApplicationContext applicationContext) {
        return new BeanDefinitionRegistryPostProcessorImpl(environment, applicationContext);
    }
}
