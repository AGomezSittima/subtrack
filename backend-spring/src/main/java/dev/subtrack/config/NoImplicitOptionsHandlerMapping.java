package dev.subtrack.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// Spring answers OPTIONS on a mapped path with 200 by itself; the API treats it as unmapped.
class NoImplicitOptionsHandlerMapping extends RequestMappingHandlerMapping {

  @Override
  protected @Nullable HandlerMethod handleNoMatch(
      Set<RequestMappingInfo> infos, String lookupPath, HttpServletRequest request)
      throws ServletException {
    HandlerMethod handler = super.handleNoMatch(infos, lookupPath, request);
    if (handler == null || !HttpMethod.OPTIONS.matches(request.getMethod())) {
      return handler;
    }
    Set<String> allowed = new HashSet<>();
    for (RequestMappingInfo info : infos) {
      if (info.getActivePatternsCondition().getMatchingCondition(request) != null) {
        for (RequestMethod method : info.getMethodsCondition().getMethods()) {
          allowed.add(method.name());
        }
      }
    }
    throw new HttpRequestMethodNotSupportedException(request.getMethod(), allowed);
  }
}
