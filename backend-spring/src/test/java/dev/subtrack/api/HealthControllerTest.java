package dev.subtrack.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

  @TempDir static Path dataDir;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("DB_PATH", () -> dataDir.resolve("subs.db").toString());
  }

  @Autowired MockMvc mvc;

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
}
