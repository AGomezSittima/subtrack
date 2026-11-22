package dev.subtrack.security;

import dev.subtrack.config.SubtrackProperties;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  static final String[] PUBLIC_PATHS = {
    "/api/v1/health", "/api/v1/auth/status", "/api/v1/auth/unlock"
  };
  static final String[] TEST_MODE_PATHS = {"/api/v1/admin/clock", "/api/v1/admin/reset"};

  @Bean
  SecurityFilterChain lock(
      HttpSecurity http,
      SubtrackProperties properties,
      LockAuthorizationManager lock,
      ProblemAuthenticationEntryPoint entryPoint,
      ProblemWriter problems) {
    http.csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .headers(headers -> headers.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions ->
            exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(entryPoint))
        .authorizeHttpRequests(requests -> {
          requests
              .dispatcherTypeMatchers(DispatcherType.ERROR)
              .permitAll()
              .requestMatchers(PUBLIC_PATHS)
              .permitAll();
          if (properties.testMode()) {
            requests.requestMatchers(TEST_MODE_PATHS).permitAll();
          }
          requests.requestMatchers("/api/v1/**").access(lock).anyRequest().permitAll();
        });
    if (!properties.testMode()) {
      http.addFilterBefore(
          new TestModePathsFilter(matcher(TEST_MODE_PATHS), problems), AuthorizationFilter.class);
    }
    return http.build();
  }

  @Bean
  WebSecurityCustomizer anyMethodReachesRouting() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setUnsafeAllowAnyHttpMethod(true);
    return web -> web.httpFirewall(firewall);
  }

  private static RequestMatcher matcher(String... paths) {
    PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
    return new OrRequestMatcher(Arrays.stream(paths).map(builder::matcher).toList());
  }
}
