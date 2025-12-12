package io.github.d_sch.webfluxconfig.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;

import io.github.d_sch.webfluxconfig.config.qualifier.OAuth2WebClient;

@Configuration
public class OAuth2WebClientConfiguration {
    @Bean
    @OAuth2WebClient
    @ConditionalOnProperty(prefix = "spring-security.oauth2.client.provider", havingValue = "primary", matchIfMissing = false)
    WebClient buildOAuth2WebClient(WebClient.Builder webClientBuilder, ReactiveClientRegistrationRepository clientRegistrations, ServerOAuth2AuthorizedClientRepository authorizedClients) {
        var oauthFilterFunction = new ServerOAuth2AuthorizedClientExchangeFilterFunction(clientRegistrations, authorizedClients);
        oauthFilterFunction.setDefaultClientRegistrationId("primary");
        return webClientBuilder
            .filter(
                oauthFilterFunction
            ).build();
    }
}
