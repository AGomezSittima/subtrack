package dev.subtrack.api;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class PathIds {

  private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");
  private static final BigInteger INT64_MAX = BigInteger.valueOf(Long.MAX_VALUE);

  private PathIds() {}

  static long parse(String text) {
    if (!INTEGER.matcher(text).matches()) {
      throw invalid(IssueCode.TYPE, null);
    }
    BigInteger value = new BigInteger(text);
    if (value.signum() <= 0) {
      throw invalid(IssueCode.MINIMUM, Map.of("minimum", 1L));
    }
    if (value.compareTo(INT64_MAX) > 0) {
      throw invalid(IssueCode.MAXIMUM, Map.of("maximum", Long.MAX_VALUE));
    }
    return value.longValueExact();
  }

  private static ProblemException invalid(IssueCode code, Map<String, Object> params) {
    return new ProblemException(
        ProblemType.VALIDATION_ERROR,
        null,
        List.of(new ValidationIssue("path", "/id", code, params)));
  }
}
