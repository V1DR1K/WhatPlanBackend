package com.wherefood.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpStatus;

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
    AdminAuditFilter adminAuditFilter(com.wherefood.web.AdminAuditService audit) {
        return new AdminAuditFilter(audit);
    }

    @Bean
    FilterRegistrationBean<AdminAuditFilter> disableServletAdminAuditRegistration(AdminAuditFilter filter) {
        FilterRegistrationBean<AdminAuditFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, CentralJwtFilter filter, RequestRateLimitFilter rateLimit,
            AdminAuditFilter adminAudit) throws Exception {
        return http.csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                .httpBasic(basic -> basic.disable())
                .formLogin(login -> login.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                ProblemDetailsSupport.write(response, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHORIZED", "Autenticación requerida.", null))
                        .accessDeniedHandler((request, response, exception) ->
                                ProblemDetailsSupport.write(response, HttpStatus.FORBIDDEN,
                                        "FORBIDDEN", "La solicitud no está permitida.", null)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout", "/api/actuator/health", "/api/actuator/health/**").permitAll()
                        .requestMatchers("/api/actuator/prometheus").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimit, CentralJwtFilter.class)
                .addFilterAfter(adminAudit, RequestRateLimitFilter.class)
                .build();
    }

}
