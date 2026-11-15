package dev.scarletnexus.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ObjectMapper mapper,
      SecurityContextRepository contexts,
      @Value("${springdoc.api-docs.enabled:true}") boolean docsEnabled)
      throws Exception {
    http.authorizeHttpRequests(
            rules -> {
              rules
                  .requestMatchers(
                      HttpMethod.GET,
                      "/api/v1/auth/csrf",
                      "/api/v1/system/status",
                      "/actuator/health",
                      "/actuator/health/liveness",
                      "/actuator/health/readiness")
                  .permitAll();
              rules.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();
              if (docsEnabled) {
                rules
                    .requestMatchers(
                        HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              }
              rules.anyRequest().authenticated();
            })
        .csrf(Customizer.withDefaults())
        .securityContext(context -> context.securityContextRepository(contexts))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, exception) -> {
                          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response.setCharacterEncoding("UTF-8");
                          mapper.writeValue(
                              response.getOutputStream(),
                              ApiError.of("UNAUTHENTICATED", "请先登录", request));
                        })
                    .accessDeniedHandler(
                        (request, response, exception) -> {
                          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response.setCharacterEncoding("UTF-8");
                          mapper.writeValue(
                              response.getOutputStream(),
                              ApiError.of("FORBIDDEN", "请求未获授权或缺少有效 CSRF 凭证", request));
                        }));
    return http.build();
  }
}
