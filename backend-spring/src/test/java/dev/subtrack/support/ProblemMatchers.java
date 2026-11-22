package dev.subtrack.support;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

public final class ProblemMatchers {

  private ProblemMatchers() {}

  public static ResultActions expectProblem(ResultActions result, String code, int status)
      throws Exception {
    return result
        .andExpect(status().is(status))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:subtrack:problem:" + code))
        .andExpect(jsonPath("$.status").value(status))
        .andExpect(jsonPath("$.title").isString())
        .andExpect(
            jsonPath("$.keys()")
                .value(everyItem(in(List.of("type", "title", "status", "detail", "errors")))));
  }
}
