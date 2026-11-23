package dev.subtrack.api;

import dev.subtrack.service.BuiltinNotDeletableException;
import dev.subtrack.service.InvalidFieldsException;
import dev.subtrack.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemHandler.class);

  private record ConstraintIssue(IssueCode code, @Nullable String limit) {}

  private static final Map<String, ConstraintIssue> CONSTRAINTS =
      Map.of(
          "NotNull", new ConstraintIssue(IssueCode.REQUIRED, null),
          "ApiTimestamp", new ConstraintIssue(IssueCode.FORMAT, null),
          "Pattern", new ConstraintIssue(IssueCode.PATTERN, null),
          "NonBlankText", new ConstraintIssue(IssueCode.PATTERN, null),
          "OneOf", new ConstraintIssue(IssueCode.ENUM, null),
          "Min", new ConstraintIssue(IssueCode.MINIMUM, "value"),
          "Max", new ConstraintIssue(IssueCode.MAXIMUM, "value"),
          "MinCodePoints", new ConstraintIssue(IssueCode.MIN_LENGTH, "value"),
          "MaxCodePoints", new ConstraintIssue(IssueCode.MAX_LENGTH, "value"),
          "Size", new ConstraintIssue(IssueCode.MAX_LENGTH, "max"));

  private static final ConstraintIssue UNKNOWN_CONSTRAINT =
      new ConstraintIssue(IssueCode.INVALID, null);

  @ExceptionHandler(ProblemException.class)
  ResponseEntity<Object> problem(ProblemException exception) {
    return Problems.response(
        exception.type(), exception.getMessage(), exception.errors(), HttpHeaders.EMPTY);
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<Object> notFound() {
    return Problems.response(ProblemType.NOT_FOUND);
  }

  @ExceptionHandler(BuiltinNotDeletableException.class)
  ResponseEntity<Object> builtinNotDeletable() {
    return Problems.response(ProblemType.BUILTIN_SERVICE_NOT_DELETABLE);
  }

  @ExceptionHandler(InvalidFieldsException.class)
  ResponseEntity<Object> invalidFields(InvalidFieldsException exception) {
    List<ValidationIssue> issues =
        exception.pointers().stream()
            .map(pointer -> ValidationIssue.body(pointer, IssueCode.INVALID))
            .toList();
    return Problems.response(ProblemType.VALIDATION_ERROR, null, issues, HttpHeaders.EMPTY);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception exception, HttpServletRequest request) {
    LOG.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), exception);
    return Problems.response(ProblemType.INTERNAL_ERROR);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Throwable cause = exception.getCause();
    if (cause instanceof UnrecognizedPropertyException unknown) {
      return invalid(IssueCode.UNKNOWN_PROPERTY, JsonPointers.fromJsonPath(unknown.getPath()));
    }
    if (cause instanceof MismatchedInputException mismatch) {
      return invalid(IssueCode.TYPE, JsonPointers.fromJsonPath(mismatch.getPath()));
    }
    if (cause == null) {
      return invalid(IssueCode.REQUIRED, "");
    }
    if (!(cause instanceof StreamReadException)) {
      LOG.warn("Unreadable request body", exception);
    }
    return Problems.response(ProblemType.MALFORMED_REQUEST);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ValidationIssue> issues = new ArrayList<>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      issues.add(issue(error));
    }
    exception
        .getBindingResult()
        .getGlobalErrors()
        .forEach(error -> issues.add(ValidationIssue.body("", IssueCode.INVALID)));
    return Problems.response(ProblemType.VALIDATION_ERROR, null, issues, HttpHeaders.EMPTY);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ProblemType type =
        switch (statusCode.value()) {
          case 400 -> ProblemType.MALFORMED_REQUEST;
          case 404 -> ProblemType.NOT_FOUND;
          case 405 -> ProblemType.METHOD_NOT_ALLOWED;
          case 415 -> ProblemType.UNSUPPORTED_MEDIA_TYPE;
          default -> {
            LOG.error("Unmapped framework error with status {}", statusCode.value(), exception);
            yield ProblemType.INTERNAL_ERROR;
          }
        };
    HttpHeaders kept = new HttpHeaders();
    if (exception instanceof HttpRequestMethodNotSupportedException unsupported) {
      kept.set(HttpHeaders.ALLOW, allow(unsupported));
    }
    return Problems.response(type, null, List.of(), kept);
  }

  private static String allow(HttpRequestMethodNotSupportedException exception) {
    Set<String> methods = new TreeSet<>();
    String[] supported = exception.getSupportedMethods();
    if (supported != null) {
      methods.addAll(Arrays.asList(supported));
    }
    if (methods.contains(HttpMethod.GET.name())) {
      methods.add(HttpMethod.HEAD.name());
    }
    return String.join(", ", methods);
  }

  private static ResponseEntity<Object> invalid(IssueCode code, String pointer) {
    return Problems.response(
        ProblemType.VALIDATION_ERROR,
        null,
        List.of(ValidationIssue.body(pointer, code)),
        HttpHeaders.EMPTY);
  }

  private static ValidationIssue issue(FieldError error) {
    String constraint = error.getCode();
    ConstraintIssue mapped =
        constraint == null
            ? UNKNOWN_CONSTRAINT
            : CONSTRAINTS.getOrDefault(constraint, UNKNOWN_CONSTRAINT);
    IssueCode code = mapped.code();
    return new ValidationIssue(
        "body", JsonPointers.fromFieldPath(error.getField()), code, params(mapped, error));
  }

  private static @Nullable Map<String, Object> params(ConstraintIssue mapped, FieldError error) {
    IssueCode code = mapped.code();
    String attribute = mapped.limit();
    if (attribute == null || !error.contains(ConstraintViolation.class)) {
      return null;
    }
    Object value =
        error
            .unwrap(ConstraintViolation.class)
            .getConstraintDescriptor()
            .getAttributes()
            .get(attribute);
    return value instanceof Number number ? Map.of(code.code(), number.longValue()) : null;
  }
}
