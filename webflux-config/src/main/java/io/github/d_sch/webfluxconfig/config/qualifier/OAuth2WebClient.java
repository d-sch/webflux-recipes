package io.github.d_sch.webfluxconfig.config.qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;

/// Creating custom qualifier annotation.
/// This can be used to annotate beans and injection points to avoid ambiguity.
/// Here is an example to qualify a WebClient bean as OAuth2WebClient.
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Qualifier
public @interface OAuth2WebClient {
}
