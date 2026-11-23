package dev.subtrack.api;

import dev.subtrack.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/settings")
class SettingsController {

  private final SettingsService settings;

  SettingsController(SettingsService settings) {
    this.settings = settings;
  }

  @GetMapping
  SettingsResponse get() {
    return SettingsResponse.of(settings.get());
  }

  @PutMapping
  SettingsResponse update(@Valid @RequestBody SettingsRequest body) {
    return SettingsResponse.of(settings.update(body.settings()));
  }
}
