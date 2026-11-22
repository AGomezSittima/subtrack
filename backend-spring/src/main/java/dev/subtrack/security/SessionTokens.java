package dev.subtrack.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SessionTokens {

  public static final String COOKIE = "subtrack_session";
  public static final String BACKGROUND_HEADER = "X-Subtrack-Background";

  private SessionTokens() {}

  public static String hash(String token) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("every Java platform provides SHA-256", e);
    }
  }
}
