package io.github.d_sch.webfluxrecipes.examples.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import io.github.d_sch.webfluxconfig.config.OAuth2WebClientConfiguration;

@AutoConfiguration
@Import({OAuth2WebClientConfiguration.class})
public class WebClientConfiguration {
}
