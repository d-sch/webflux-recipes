package io.github.d_sch.webfluxconfig.config;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
public class AbstractR2DBCConfiguration {
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
