package dev.subtrack.support;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class ProductionApiTest {

  protected static final Path DATABASE = TestDatabase.shared("production.db");

  @Autowired protected MockMvc mvc;

  @DynamicPropertySource
  static void production(DynamicPropertyRegistry registry) {
    registry.add("subtrack.test-mode", () -> false);
    registry.add("subtrack.db-path", DATABASE::toString);
  }
}
