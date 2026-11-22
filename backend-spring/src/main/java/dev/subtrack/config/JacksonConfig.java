package dev.subtrack.config;

import java.time.Instant;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

  @Bean
  JsonMapperBuilderCustomizer strictJson() {
    return builder ->
        builder
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(
                LogicalType.Textual,
                config ->
                    config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .addModule(
                new SimpleModule("subtrack-timestamps")
                    .addSerializer(Instant.class, new TimestampSerializer()));
  }
}
