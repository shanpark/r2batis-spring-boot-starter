package io.github.shanpark.r2batis.configure;

import io.github.shanpark.r2batis.core.R2batisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
public class R2batisAutoConfiguration {

    public static R2batisProperties defaultR2batisProperties;

    public static boolean isTesting = false;

    @Bean
    public static BeanPostProcessor r2batisBeanPostProcessor(ConfigurableApplicationContext applicationContext) {
        return new R2batisBeanPostProcessor(applicationContext);
    }
}
