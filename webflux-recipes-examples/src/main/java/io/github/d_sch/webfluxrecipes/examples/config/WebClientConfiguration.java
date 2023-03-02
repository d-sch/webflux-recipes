package io.github.d_sch.webfluxrecipes.examples.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.github.d_sch.webfluxconfig.config.qualifier.OAuth2WebClient;

@Configuration
@Import(OAuth2WebClient.class)
public class WebClientConfiguration {
}
