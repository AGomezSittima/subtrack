package dev.subtrack.api;

import dev.subtrack.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@ConditionalOnBooleanProperty("subtrack.test-mode")
class AdminController {

  private final AdminService admin;

  AdminController(AdminService admin) {
    this.admin = admin;
  }

  @PostMapping("/clock")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void setClock(@Valid @RequestBody ClockSetRequest body) {
    admin.setClock(body.instant());
  }

  @PostMapping("/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void reset() {
    admin.reset();
  }
}
