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

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, CentralJwtFilter filter) throws Exception {
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
                .build();
    }

    private static void writeError(HttpServletResponse response, int status, String code) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"detail\":\"" + (status == HttpServletResponse.SC_FORBIDDEN ? "Access denied" : "Authentication required") + "\"}");
    }
}
