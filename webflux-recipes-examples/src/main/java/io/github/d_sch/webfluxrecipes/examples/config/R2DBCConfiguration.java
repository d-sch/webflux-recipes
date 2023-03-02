package io.github.d_sch.webfluxrecipes.examples.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.d_sch.webfluxconfig.config.AbstractR2DBCConfiguration;
import io.r2dbc.spi.ConnectionFactory;

@Configuration
public class R2DBCConfiguration extends AbstractR2DBCConfiguration {
    
    @Bean
    ConnectionFactory getDemoConnectionFactory(AbstractR2DBCConfiguration properties) {
        return ConnectionFactoryBuilder
            .withOptions(buildConnectionFactoryOptions(properties.getOptions().get("demo"))
        ).build();
    }
    
}
