package dev.subtrack.api;

import static dev.subtrack.support.ProblemMatchers.expectProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.subtrack.support.Rows;
import dev.subtrack.support.TestDatabase;
import dev.subtrack.support.TestModeApiTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AdminControllerTest extends TestModeApiTest {

  private static final String CLOCK = "/api/v1/admin/clock";
  private static final String RESET = "/api/v1/admin/reset";

  private ResultActions setClock(String json) throws Exception {
    return mvc.perform(post(CLOCK).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private void expectIssues(ResultActions result, String errorsJson) throws Exception {
    expectProblem(result, "validation-error", 422)
        .andExpect(content().json("{\"errors\": " + errorsJson + "}", JsonCompareMode.LENIENT))
        .andExpect(jsonPath("$.errors.length()").value(errorsJson.split("\\{").length - 1));
  }

  @Test
  void settingTheClockPinsNow() throws Exception {
    setClock("{\"now\": \"2026-03-28T23:30:00.000Z\"}")
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    assertThat(clock.instant()).isEqualTo(Instant.parse("2026-03-28T23:30:00Z"));
  }

  @Test
  void resetRestoresTheMigratedStateAndUnpinsTheClock() throws Exception {
    Rows.fillEveryUserTable(DATABASE);
    setClock("{\"now\": \"2000-01-01T00:00:00.000Z\"}");

    mvc.perform(post(RESET)).andExpect(status().isNoContent());

    for (String table :
        List.of("subscription", "payment", "session", "setting", "catalog_service")) {
      assertThat(TestDatabase.table(DATABASE, table))
          .isEqualTo(TestDatabase.table(TEMPLATE, table));
    }
    assertThat(clock.instant()).isAfter(Instant.parse("2000-01-02T00:00:00Z"));
  }

  @Test
  void aMissingNowIsReported() throws Exception {
    expectIssues(
        setClock("{}"),
        "[{\"location\": \"body\", \"pointer\": \"/now\", \"code\": \"required\"}]");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-11-10T15:18:00Z",
        "2026-11-10T15:18:00.000+01:00",
        "2026-02-30T00:00:00.000Z",
        "2026-11-10T23:59:60.000Z",
        "0000-01-01T00:00:00.000Z",
        ""
      })
  void aNonCanonicalNowIsAFormatError(String now) throws Exception {
    expectIssues(
        setClock("{\"now\": \"" + now + "\"}"),
        "[{\"location\": \"body\", \"pointer\": \"/now\", \"code\": \"format\"}]");
  }

  @Test
  void structuralErrorsAreReported() throws Exception {
    expectProblem(setClock("{\"now\": 5}"), "validation-error", 422)
        .andExpect(jsonPath("$.errors[?(@.pointer == '/now' && @.code == 'type')]").exists());
    expectProblem(
            setClock("{\"now\": \"2026-11-10T15:18:00.000Z\", \"extra\": 1}"),
            "validation-error",
            422)
        .andExpect(
            jsonPath("$.errors[?(@.pointer == '/extra' && @.code == 'unknown_property')]")
                .exists());
    expectProblem(setClock("[]"), "validation-error", 422)
        .andExpect(jsonPath("$.errors[?(@.pointer == '' && @.code == 'type')]").exists());
  }

  @Test
  void malformedJsonIsAMalformedRequest() throws Exception {
    expectProblem(setClock("{bad"), "malformed-request", 400);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"text/plain", "application/x-www-form-urlencoded", "multipart/form-data", ""})
  void aBodyThatIsNotJsonIsAnUnsupportedMediaType(String contentType) throws Exception {
    MockHttpServletRequestBuilder request =
        post(CLOCK).content("{\"now\": \"2026-11-10T15:18:00.000Z\"}");
    if (!contentType.isEmpty()) {
      request.contentType(contentType);
    }

    expectProblem(mvc.perform(request), "unsupported-media-type", 415);
  }

  @Test
  void aMissingBodyIsARequiredError() throws Exception {
    expectIssues(
        mvc.perform(post(CLOCK)),
        "[{\"location\": \"body\", \"pointer\": \"\", \"code\": \"required\"}]");
  }
}
