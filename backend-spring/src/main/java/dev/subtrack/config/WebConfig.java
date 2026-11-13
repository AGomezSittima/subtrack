package dev.subtrack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves every {@code @RestController} under {@link #API_PREFIX}, so controllers map only their own
 * paths ({@code @GetMapping("/health")}) and the prefix is defined once.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

  /** Base path of the API: {@code servers} in contract/openapi.yaml. */
  public static final String API_PREFIX = "/api/v1";

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forAnnotation(RestController.class));
  }
}
