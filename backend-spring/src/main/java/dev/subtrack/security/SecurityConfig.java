package dev.subtrack.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.StrictHttpFirewall;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  @Bean
  SecurityFilterChain lock(
      HttpSecurity http, LockAuthorizationManager lock, ProblemAuthenticationEntryPoint problems) {
    http.csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .headers(headers -> headers.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions ->
            exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .authorizeHttpRequests(requests ->
            requests
                .dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers("/api/v1/health", "/api/v1/auth/status", "/api/v1/auth/unlock")
                .permitAll()
                .requestMatchers("/api/v1/**")
                .access(lock)
                .anyRequest()
                .permitAll());
    return http.build();
  }

  @Bean
  WebSecurityCustomizer anyMethodReachesRouting() {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setUnsafeAllowAnyHttpMethod(true);
    return web -> web.httpFirewall(firewall);
  }
}
