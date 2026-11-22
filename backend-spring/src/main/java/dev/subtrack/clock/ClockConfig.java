package dev.subtrack.clock;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

  @Bean
  @ConditionalOnBooleanProperty(
      name = "subtrack.test-mode",
      havingValue = false,
      matchIfMissing = true)
  Clock systemClock() {
    return Clock.system(AppZone.ZONE);
  }

  @Bean
  @ConditionalOnBooleanProperty("subtrack.test-mode")
  SettableClock settableClock() {
    return new SettableClock(Clock.system(AppZone.ZONE));
  }
}
