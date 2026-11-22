package dev.subtrack.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.subtrack.support.TestDatabase;
import dev.subtrack.support.TestModeApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;

class HealthControllerTest extends TestModeApiTest {

  @Test
  void healthReportsSpringBackend() throws Exception {
    mvc.perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"status": "ok", "backend": "spring", "version": "0.1.0", "last_scheduler_run": null}
                    """,
                    JsonCompareMode.STRICT));
  }

  @Test
  void healthReadsTheLastSchedulerRunFromTheSettings() throws Exception {
    TestDatabase.execute(
        DATABASE,
        "UPDATE setting SET value = '2026-11-10T15:00:00.000Z' WHERE key = 'last_scheduler_run'");

    mvc.perform(get("/api/v1/health"))
        .andExpect(jsonPath("$.last_scheduler_run").value("2026-11-10T15:00:00.000Z"));
  }

  @Test
  void healthAnswersHeadLikeGet() throws Exception {
    mvc.perform(head("/api/v1/health")).andExpect(status().isOk());
  }

  @Test
  void controllersAreOnlyServedUnderTheApiPrefix() throws Exception {
    mvc.perform(get("/health")).andExpect(status().isNotFound());
  }
}
