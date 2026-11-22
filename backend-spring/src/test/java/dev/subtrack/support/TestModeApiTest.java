package dev.subtrack.support;

import dev.subtrack.clock.SettableClock;
import dev.subtrack.service.AdminService;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class TestModeApiTest {

  protected static final Path DATABASE = TestDatabase.shared("test-mode.db");
  protected static final Path TEMPLATE = TestDatabase.shared("test-mode-template.db");

  @Autowired protected MockMvc mvc;
  @Autowired protected SettableClock clock;
  @Autowired private AdminService admin;

  @DynamicPropertySource
  static void testMode(DynamicPropertyRegistry registry) {
    registry.add("subtrack.test-mode", () -> true);
    registry.add("subtrack.db-path", DATABASE::toString);
    registry.add("subtrack.template-db-path", TEMPLATE::toString);
  }

  @BeforeEach
  void freshDatabase() {
    admin.reset();
  }
}
