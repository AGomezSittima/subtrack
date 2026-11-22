package dev.subtrack.security;

import dev.subtrack.api.Problem;
import dev.subtrack.api.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final JsonMapper json;

  ProblemAuthenticationEntryPoint(JsonMapper json) {
    this.json = json;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    unauthorized(response);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    unauthorized(response);
  }

  private void unauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(ProblemType.UNAUTHORIZED.status());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(
        response.getOutputStream(), Problem.of(ProblemType.UNAUTHORIZED, null, List.of()));
  }
}
