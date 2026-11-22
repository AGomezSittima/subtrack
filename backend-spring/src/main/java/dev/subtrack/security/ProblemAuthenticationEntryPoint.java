package dev.subtrack.security;

import dev.subtrack.api.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ProblemWriter problems;

  ProblemAuthenticationEntryPoint(ProblemWriter problems) {
    this.problems = problems;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    problems.write(response, ProblemType.UNAUTHORIZED);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    problems.write(response, ProblemType.UNAUTHORIZED);
  }
}
