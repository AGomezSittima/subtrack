package dev.subtrack.security;

import dev.subtrack.api.ProblemType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

final class TestModePathsFilter extends OncePerRequestFilter {

  private final RequestMatcher paths;
  private final ProblemWriter problems;

  TestModePathsFilter(RequestMatcher paths, ProblemWriter problems) {
    this.paths = paths;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (paths.matches(request)) {
      problems.write(response, ProblemType.NOT_FOUND);
      return;
    }
    chain.doFilter(request, response);
  }
}
