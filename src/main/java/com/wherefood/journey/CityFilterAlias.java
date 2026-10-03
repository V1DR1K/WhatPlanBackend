package com.wherefood.journey;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Component
@Order(50)
public class CityFilterAlias extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String city = request.getParameter("cityId"), zone = request.getParameter("zoneId");
        if (request.getRequestURI().startsWith("/api/") && city != null) {
            if (zone != null && !zone.equals(city)) {
                com.wherefood.config.ProblemDetailsSupport.write(
                        response,
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "LOCATION_CONFLICT",
                        "cityId y zoneId deben coincidir",
                        null);
                return;
            }
            Map<String, String[]> params = new HashMap<>(request.getParameterMap());
            params.put("zoneId", new String[] {city});
            chain.doFilter(
                    new HttpServletRequestWrapper(request) {
                        @Override
                        public String getParameter(String name) {
                            String[] v = params.get(name);
                            return v == null ? null : v[0];
                        }

                        @Override
                        public Map<String, String[]> getParameterMap() {
                            return Collections.unmodifiableMap(params);
                        }

                        @Override
                        public String[] getParameterValues(String name) {
                            return params.get(name);
                        }

                        @Override
                        public Enumeration<String> getParameterNames() {
                            return Collections.enumeration(params.keySet());
                        }
                    },
                    response);
            return;
        }
        chain.doFilter(request, response);
    }
}
