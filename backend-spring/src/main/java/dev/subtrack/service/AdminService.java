package dev.subtrack.service;

import dev.subtrack.clock.SettableClock;
import dev.subtrack.repository.TemplateRepository;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBooleanProperty("subtrack.test-mode")
public class AdminService {

  private final TemplateRepository template;
  private final SettableClock clock;

  public AdminService(TemplateRepository template, SettableClock clock) {
    this.template = template;
    this.clock = clock;
  }

  public void reset() {
    template.restore();
    clock.unpin();
  }

  public void setClock(Instant now) {
    clock.pin(now);
  }
}
