package dev.subtrack.api;

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

  private static final Map<String, IssueCode> CONSTRAINT_CODES =
      Map.of(
          "NotNull", IssueCode.REQUIRED,
          "ApiTimestamp", IssueCode.FORMAT,
          "Pattern", IssueCode.PATTERN,
          "Min", IssueCode.MINIMUM,
          "Max", IssueCode.MAXIMUM);

  private static final Map<IssueCode, String> CONSTRAINT_PARAMS =
      Map.of(IssueCode.MINIMUM, "value", IssueCode.MAXIMUM, "value");

  @ExceptionHandler(ProblemException.class)
  ResponseEntity<Object> problem(ProblemException exception) {
    return Problems.response(
        exception.type(), exception.getMessage(), exception.errors(), HttpHeaders.EMPTY);
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
    IssueCode code =
        constraint == null
            ? IssueCode.INVALID
            : CONSTRAINT_CODES.getOrDefault(constraint, IssueCode.INVALID);
    return new ValidationIssue(
        "body", JsonPointers.fromFieldPath(error.getField()), code, params(code, error));
  }

  private static @Nullable Map<String, Object> params(IssueCode code, FieldError error) {
    String attribute = CONSTRAINT_PARAMS.get(code);
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
