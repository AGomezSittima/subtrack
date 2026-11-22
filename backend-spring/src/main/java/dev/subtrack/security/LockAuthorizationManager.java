package dev.subtrack.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

@Component
class LockAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

  private final LockEnforcer enforcer;

  LockAuthorizationManager(LockEnforcer enforcer) {
    this.enforcer = enforcer;
  }

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    HttpServletRequest request = context.getRequest();
    Cookie cookie = WebUtils.getCookie(request, SessionTokens.COOKIE);
    boolean background = "1".equals(request.getHeader(SessionTokens.BACKGROUND_HEADER));
    return new AuthorizationDecision(
        enforcer.allows(cookie == null ? null : cookie.getValue(), background));
  }
}
