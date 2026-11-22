package dev.subtrack.api;

import dev.subtrack.config.SubtrackProperties;
import dev.subtrack.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /health: liveness and backend identity (public endpoint). Prefixed by WebConfig. */
@RestController
class HealthController {

  private final SubtrackProperties properties;
  private final HealthService health;

  HealthController(SubtrackProperties properties, HealthService health) {
    this.properties = properties;
    this.health = health;
  }

  @GetMapping("/health")
  HealthResponse health() {
    return new HealthResponse("ok", "spring", properties.version(), health.lastSchedulerRun());
  }
}
