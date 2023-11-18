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


package io.github.d_sch.webfluxconfig.config;

import java.util.Map;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.d_sch.webfluxcommon.common.Predicates;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@ConfigurationProperties(prefix = "r2dbc")
@RequiredArgsConstructor
public class R2DBCConfigurationProperties {
    @Getter
    @Setter
    private Map<String, R2dbcProperties> options;

    public static ConnectionFactoryOptions.Builder buildConnectionFactoryOptions(R2dbcProperties r2dbcProperties) {
		ConnectionFactoryOptions urlOptions = ConnectionFactoryOptions.parse(r2dbcProperties.getUrl());
		ConnectionFactoryOptions.Builder optionsBuilder = urlOptions.mutate();
        Predicates.set(r2dbcProperties.getUsername(), s -> s != null && s.length() > 0, s -> optionsBuilder.option(ConnectionFactoryOptions.USER, s));
        Predicates.set(r2dbcProperties.getPassword(), s -> s != null && s.length() > 0, s -> optionsBuilder.option(ConnectionFactoryOptions.PASSWORD, s));
        Predicates.set(r2dbcProperties.isGenerateUniqueName() ? r2dbcProperties.determineUniqueName() : r2dbcProperties.getName(), s -> s != null && s.length() > 0, s -> optionsBuilder.option(ConnectionFactoryOptions.PASSWORD, s));
		if (r2dbcProperties.getProperties() != null) {
			r2dbcProperties.getProperties().forEach((key, value) -> optionsBuilder.option(Option.valueOf(key), value));
		}
		return optionsBuilder;    
    }
}
