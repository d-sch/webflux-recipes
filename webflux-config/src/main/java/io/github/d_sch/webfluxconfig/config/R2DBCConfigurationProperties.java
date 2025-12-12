package io.github.d_sch.webfluxconfig.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcProperties;

import io.github.d_sch.webfluxcommon.common.Predicates;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/// Configuration properties for R2DBC connections.
/// These properties can be defined in application properties or YAML files with the prefix "r2dbc".
/// The 'options' map allows defining multiple named R2DBC configurations.
@ConfigurationProperties(prefix = "r2dbc")
@RequiredArgsConstructor
public class R2DBCConfigurationProperties {
    @Getter
    @Setter
    private Map<String, R2dbcProperties> options;

    public ConnectionFactoryOptions.Builder getConnectionFactoryOptions(String name) {
        R2dbcProperties r2dbcProperties = options.get(name);
        if (r2dbcProperties == null) {
            throw new IllegalArgumentException("No R2DBC properties found for name: " + name);
        }
        return buildConnectionFactoryOptions(r2dbcProperties);
    }

    private static ConnectionFactoryOptions.Builder buildConnectionFactoryOptions(R2dbcProperties r2dbcProperties) {
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
