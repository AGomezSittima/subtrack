package dev.subtrack.api;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.jspecify.annotations.Nullable;

// Parsed here, like Starlette does: Tomcat rejects malformed chunks and Spring joins repeated
// values.
final class QueryString {

  private QueryString() {}

  static Map<String, String> parse(@Nullable String raw) {
    Map<String, String> values = new HashMap<>();
    if (raw == null) {
      return values;
    }
    for (String chunk : raw.split("&")) {
      if (chunk.isEmpty()) {
        continue;
      }
      int equals = chunk.indexOf('=');
      String name = equals < 0 ? chunk : chunk.substring(0, equals);
      String value = equals < 0 ? "" : chunk.substring(equals + 1);
      values.put(decode(name), decode(value));
    }
    return values;
  }

  private static String decode(String text) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    int index = 0;
    while (index < text.length()) {
      char c = text.charAt(index);
      if (c == '%' && isHex(text, index + 1)) {
        bytes.write(HexFormat.fromHexDigits(text, index + 1, index + 3));
        index += 3;
      } else {
        byte[] encoded = String.valueOf(c == '+' ? ' ' : c).getBytes(StandardCharsets.UTF_8);
        bytes.writeBytes(encoded);
        index++;
      }
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }

  private static boolean isHex(String text, int start) {
    return start + 2 <= text.length()
        && HexFormat.isHexDigit(text.charAt(start))
        && HexFormat.isHexDigit(text.charAt(start + 1));
  }
}
