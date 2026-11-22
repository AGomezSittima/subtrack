package dev.subtrack.api;

import static dev.subtrack.support.ProblemMatchers.expectProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.subtrack.clock.SettableClock;
import dev.subtrack.support.ProductionApiTest;
import java.time.Clock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class OutsideTestModeTest extends ProductionApiTest {

  @Autowired Clock clock;

  @ParameterizedTest
  @ValueSource(
      strings = {"/api/v1/admin/clock", "/api/v1/admin/reset", "/api/v1/admin/run-scheduler"})
  void testEndpointsAreNotFound(String path) throws Exception {
    expectProblem(
        mvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"now\": \"2026-11-10T15:18:00.000Z\"}")),
        "not-found",
        404);
    assertThat(clock).isNotInstanceOf(SettableClock.class);
  }
}
