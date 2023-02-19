package io.github.d_sch.webfluxcached.config;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "r2dbc")
public class R2DBCConfig {
    @Getter
    @Setter
    private Map<String, R2dbcProperties> test;

    @Bean
    ConnectionFactory getDemoConnectionFactory() {
        return ConnectionFactoryBuilder
            .withOptions(buildConnectionFactoryOptions(test.get("demo"))
        ).build();
    }

    private static <T> void predicateSet(T value, Predicate<T> condition, Consumer<T> action) {
        if(condition.test(value)){
            action.accept(value);
        }
    }

    public static ConnectionFactoryOptions.Builder buildConnectionFactoryOptions(R2dbcProperties r2dbcProperties) {
		ConnectionFactoryOptions urlOptions = ConnectionFactoryOptions.parse(r2dbcProperties.getUrl());
		ConnectionFactoryOptions.Builder optionsBuilder = urlOptions.mutate();
        predicateSet(r2dbcProperties.getUsername(), s -> s != null && s.length() > 0, s -> optionsBuilder.option(ConnectionFactoryOptions.USER, s));
        predicateSet(r2dbcProperties.getPassword(), s -> s != null && s.length() > 0, s -> optionsBuilder.option(ConnectionFactoryOptions.PASSWORD, s));
        predicateSet(r2dbcProperties.isGenerateUniqueName() ? r2dbcProperties.determineUniqueName() : r2dbcProperties.getName(), s -> s != null && s.length() > 0, s -> optionsBuilder.option(ConnectionFactoryOptions.PASSWORD, s));
		if (r2dbcProperties.getProperties() != null) {
			r2dbcProperties.getProperties().forEach((key, value) -> optionsBuilder.option(Option.valueOf(key), value));
		}
		return optionsBuilder;    
    }
}
