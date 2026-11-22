package dev.subtrack.security;

import static dev.subtrack.support.ProblemMatchers.expectProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.subtrack.persistence.IsoText;
import dev.subtrack.support.Rows;
import dev.subtrack.support.TestDatabase;
import dev.subtrack.support.TestModeApiTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

class LockEnforcementTest extends TestModeApiTest {

  private static final Instant NOW = Instant.parse("2026-11-10T15:18:00Z");
  private static final String COOKIE_VALUE = "value-of-the-session-cookie";
  private static final Cookie SESSION = new Cookie(SessionTokens.COOKIE, COOKIE_VALUE);
  private static final String PROTECTED = "/api/v1/admin/run-scheduler";

  private static void addSession(Instant lastActivityAt) {
    String at = IsoText.formatTimestamp(lastActivityAt);
    TestDatabase.execute(
        DATABASE,
        "INSERT INTO session (token_hash, created_at, last_activity_at) VALUES (?, ?, ?)",
        SessionTokens.hash(COOKIE_VALUE),
        at,
        at);
  }

  private static Object lastActivity() {
    return TestDatabase.rows(DATABASE, "SELECT last_activity_at FROM session").get(0).get(0);
  }

  @Test
  void everythingIsOpenWhileNoPasscodeIsSet() throws Exception {
    expectProblem(mvc.perform(post(PROTECTED)), "not-found", 404);
  }

  @Test
  void sessionTokensAreStoredAsLowercaseHexSha256() {
    assertThat(SessionTokens.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Nested
  class Locked {

    @BeforeEach
    void lock() {
      Rows.setPasscode(DATABASE, "idle_15");
      clock.pin(NOW);
    }

    @ParameterizedTest
    @CsvSource({
      "POST, /api/v1/admin/run-scheduler",
      "GET, /api/v1/subscriptions",
      "GET, /api/v1",
      "DELETE, /api/v1/admin/run-scheduler"
    })
    void aPasscodeLocksEveryApiPathWithoutASession(String method, String path) throws Exception {
      expectProblem(mvc.perform(request(HttpMethod.valueOf(method), path)), "unauthorized", 401);
    }

    @Test
    void aLockedRequestIsRefusedBeforeItsBodyIsRead() throws Exception {
      expectProblem(
          mvc.perform(
              post("/api/v1/subscriptions")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{bad")),
          "unauthorized",
          401);
    }

    @Test
    void testModeEndpointsAreNotBehindTheLock() throws Exception {
      mvc.perform(
              post("/api/v1/admin/clock")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"now\": \"" + Rows.TS + "\"}"))
          .andExpect(status().isNoContent());
      mvc.perform(post("/api/v1/admin/reset")).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @CsvSource({
      "GET, /api/v1/health, 200",
      "GET, /api/v1/auth/status, 404",
      "POST, /api/v1/auth/unlock, 404"
    })
    void publicPathsStayOpen(String method, String path, int status) throws Exception {
      mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().is(status));
    }

    @Test
    void pathsOutsideTheApiAreNotFound() throws Exception {
      expectProblem(mvc.perform(get("/api/v2/health")), "not-found", 404);
    }

    @Test
    void anUnknownSessionIsRefused() throws Exception {
      addSession(NOW);

      expectProblem(
          mvc.perform(post(PROTECTED).cookie(new Cookie(SessionTokens.COOKIE, "other"))),
          "unauthorized",
          401);
    }

    @Test
    void aValidSessionPassesAndRenewsItsActivity() throws Exception {
      addSession(NOW.minus(Duration.ofMinutes(10)));

      expectProblem(mvc.perform(get("/api/v1/nope").cookie(SESSION)), "not-found", 404);

      assertThat(lastActivity()).isEqualTo("2026-11-10T15:18:00.000Z");
    }

    @Test
    void aBackgroundRequestPassesWithoutRenewingTheSession() throws Exception {
      addSession(NOW.minus(Duration.ofMinutes(10)));

      expectProblem(
          mvc.perform(
              get("/api/v1/nope").cookie(SESSION).header(SessionTokens.BACKGROUND_HEADER, "1")),
          "not-found",
          404);
      assertThat(lastActivity()).isEqualTo("2026-11-10T15:08:00.000Z");
    }

    static Stream<Arguments> idleCases() {
      return Stream.of(
          Arguments.of("idle_15", Duration.ofMinutes(15), true),
          Arguments.of("idle_15", Duration.ofMinutes(15).plusMillis(1), false),
          Arguments.of("idle_5", Duration.ofMinutes(5).plusSeconds(1), false),
          Arguments.of("idle_60", Duration.ofMinutes(59), true),
          Arguments.of("manual", Duration.ofDays(30), true),
          Arguments.of("on_open", Duration.ofDays(30), true));
    }

    @ParameterizedTest
    @MethodSource("idleCases")
    void aSessionExpiresAfterTheIdleTimeOfTheLockPolicy(
        String policy, Duration idle, boolean allowed) throws Exception {
      Rows.setPasscode(DATABASE, policy);
      addSession(NOW.minus(idle));

      mvc.perform(get("/api/v1/nope").cookie(SESSION)).andExpect(status().is(allowed ? 404 : 401));
    }
  }
}
