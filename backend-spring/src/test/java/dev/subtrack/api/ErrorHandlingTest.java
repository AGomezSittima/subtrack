package dev.subtrack.api;

import static dev.subtrack.support.ProblemMatchers.expectProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import dev.subtrack.support.TestModeApiTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Import(ErrorHandlingTest.Exploding.class)
class ErrorHandlingTest extends TestModeApiTest {

  @RestController
  static class Exploding {
    @GetMapping("/explode")
    String explode() {
      throw new IllegalStateException("secret internals");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/nope", "/api/v1", "/api/v1/health/", "/elsewhere"})
  void anUnknownRouteIsNotFound(String path) throws Exception {
    expectProblem(mvc.perform(get(path)), "not-found", 404)
        .andExpect(jsonPath("$.detail").doesNotExist())
        .andExpect(jsonPath("$.instance").doesNotExist());
  }

  @ParameterizedTest
  @ValueSource(strings = {"DELETE", "PUT", "PATCH", "OPTIONS"})
  void aKnownPathWithAnotherMethodIsNotAllowed(String method) throws Exception {
    expectProblem(
        mvc.perform(request(HttpMethod.valueOf(method), "/api/v1/health")),
        "method-not-allowed",
        405);
  }

  @Test
  void anUnexpectedErrorIsAnInternalErrorWithoutInternals() throws Exception {
    String body =
        expectProblem(mvc.perform(get("/api/v1/explode")), "internal-error", 500)
            .andExpect(jsonPath("$.detail").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("secret").doesNotContain("IllegalStateException");
  }
}
