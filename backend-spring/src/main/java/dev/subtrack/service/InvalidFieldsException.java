package dev.subtrack.service;

import java.util.List;

public class InvalidFieldsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<String> pointers;

  public InvalidFieldsException(List<String> pointers) {
    super(String.join(", ", pointers));
    this.pointers = List.copyOf(pointers);
  }

  public List<String> pointers() {
    return pointers;
  }
}
