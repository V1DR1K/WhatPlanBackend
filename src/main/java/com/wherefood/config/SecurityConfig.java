package com.wherefood.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    RequestRateLimitFilter rateLimitFilter(SharedRateLimiter limiter,
            @Value("${app.rate-limit.trusted-proxy-addresses:}") String trustedProxyAddresses) {
        return new RequestRateLimitFilter(limiter, trustedProxyAddresses);
    }

    @Bean
    FilterRegistrationBean<RequestRateLimitFilter> disableServletRateLimitRegistration(RequestRateLimitFilter filter) {
        FilterRegistrationBean<RequestRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, CentralJwtFilter filter, RequestRateLimitFilter rateLimit) throws Exception {
        return http.csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                .httpBasic(basic -> basic.disable())
                .formLogin(login -> login.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED"))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/actuator/health", "/api/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimit, CentralJwtFilter.class)
                .build();
    }

    private static void writeError(HttpServletResponse response, int status, String code) throws java.io.IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + code + "\",\"status\":" + status + ",\"detail\":\"" + (status == HttpServletResponse.SC_FORBIDDEN ? "Access denied" : "Authentication required") + "\"}");
    }
}
