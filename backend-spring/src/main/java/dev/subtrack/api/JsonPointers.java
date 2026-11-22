package dev.subtrack.api;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;

final class JsonPointers {

  private static final Pattern FIELD_PATH_PARTS = Pattern.compile("\\.|\\[|]\\.?");
  private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

  private JsonPointers() {}

  static String fromJsonPath(List<JacksonException.Reference> path) {
    StringBuilder pointer = new StringBuilder();
    for (JacksonException.Reference reference : path) {
      String name = reference.getPropertyName();
      pointer
          .append('/')
          .append(name == null ? String.valueOf(reference.getIndex()) : escape(name));
    }
    return pointer.toString();
  }

  static String fromFieldPath(String fieldPath) {
    StringBuilder pointer = new StringBuilder();
    for (String part : FIELD_PATH_PARTS.split(fieldPath)) {
      if (!part.isEmpty()) {
        pointer.append('/').append(escape(isIndex(part) ? part : snakeCase(part)));
      }
    }
    return pointer.toString();
  }

  private static String snakeCase(String javaName) {
    return WORD_BOUNDARY.matcher(javaName).replaceAll("_").toLowerCase(Locale.ROOT);
  }

  private static boolean isIndex(String part) {
    return part.chars().allMatch(Character::isDigit);
  }

  private static String escape(String part) {
    return part.replace("~", "~0").replace("/", "~1");
  }
}
