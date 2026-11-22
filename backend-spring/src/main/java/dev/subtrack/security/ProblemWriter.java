package dev.subtrack.security;

import dev.subtrack.api.Problem;
import dev.subtrack.api.ProblemType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProblemWriter {

  private final JsonMapper json;

  ProblemWriter(JsonMapper json) {
    this.json = json;
  }

  void write(HttpServletResponse response, ProblemType type) throws IOException {
    response.setStatus(type.status());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), Problem.of(type, null, List.of()));
  }
}
