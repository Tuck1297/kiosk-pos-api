package io.github.tuck1297.kioskpos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves every API controller under /v1, matching the server URL in openapi.yaml.
 * Actuator endpoints (/actuator/**) are not affected.
 */
@Configuration
public class ApiPathConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
                HandlerTypePredicate.forBasePackage("io.github.tuck1297.kioskpos.web"));
    }
}
