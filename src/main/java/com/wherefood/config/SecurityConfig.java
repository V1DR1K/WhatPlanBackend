package com.wherefood.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.core.annotation.Order;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain publicFilmReads(HttpSecurity http) throws Exception {
        return http.securityMatcher(new AntPathRequestMatcher("/api/films/**", "GET"))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, CentralJwtFilter filter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(login -> login.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/actuator/**").permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/api/films/**", "GET")).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/categories", "/api/highlight-tags", "/api/places", "/api/places/*", "/api/places/*/visits", "/api/places/*/item-dates", "/api/places/*/photo", "/api/place-visits/*", "/api/place-visit-photos/*", "/api/items", "/api/items/*/photo", "/api/watch-platforms", "/api/film-genres", "/api/how-cook/recipes", "/api/how-cook/recipes/*", "/api/how-cook/recipes/*/photo", "/api/how-cook/cookings", "/api/how-cook/cookings/*", "/api/why-fun/categories", "/api/why-fun/plans", "/api/why-fun/plans/*", "/api/why-fun/photos/*", "/api/why-fun/activities", "/api/why-fun/activities/*", "/api/why-fun/activities/*/photo", "/api/why-fun/activities/*/visits", "/api/why-fun/activity-visits/*", "/api/why-fun/activity-visit-photos/*", "/api/when-dates/photos/*").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
