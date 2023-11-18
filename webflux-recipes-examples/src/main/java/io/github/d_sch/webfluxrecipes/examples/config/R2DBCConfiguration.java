/*
 * Copyright 2021 - 2023 d-sch
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and,
 * limitations under the License.
 */


package io.github.d_sch.webfluxrecipes.examples.config;

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
