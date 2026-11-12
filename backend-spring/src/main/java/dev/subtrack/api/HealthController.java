package dev.subtrack.api;

import dev.subtrack.config.SubtrackProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /health: liveness and backend identity (public endpoint). */
@RestController
@RequestMapping("/api/v1")
class HealthController {

  private final SubtrackProperties properties;

  HealthController(SubtrackProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/health")
  HealthResponse health() {
    return new HealthResponse("ok", "spring", properties.version(), null);
  }
}
