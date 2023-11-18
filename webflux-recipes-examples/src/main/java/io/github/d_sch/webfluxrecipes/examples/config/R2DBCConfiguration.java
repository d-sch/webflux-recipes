package io.github.d_sch.webfluxrecipes.examples.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.d_sch.webfluxconfig.config.R2DBCConfigurationProperties;
import io.r2dbc.spi.ConnectionFactory;

@Configuration
@EnableConfigurationProperties(R2DBCConfigurationProperties.class)
public class R2DBCConfiguration extends R2DBCConfigurationProperties {
    
    @Bean
    ConnectionFactory getDemoConnectionFactory(R2DBCConfigurationProperties properties) {
        return ConnectionFactoryBuilder
            .withOptions(buildConnectionFactoryOptions(properties.getOptions().get("demo"))
        ).build();
    }
    
}
