package com.wherefood.journey;

import jakarta.servlet.MultipartConfigElement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keep the servlet envelope large enough for the separately enforced upload limits. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JourneyMultipartConfiguration {
    @Bean
    MultipartConfigElement multipartConfigElement(
            MultipartProperties properties,
            @Value("${app.journey.max-upload-bytes:10485760}") long journeyBytes,
            @Value("${app.images.max-upload-bytes:10485760}") long imageBytes) {
        if (journeyBytes < 1 || imageBytes < 1)
            throw new IllegalArgumentException("Upload limits must be positive");
        MultipartConfigElement original = properties.createMultipartConfig();
        long fileBytes = Math.max(original.getMaxFileSize(), Math.max(journeyBytes, imageBytes));
        long requestBytes =
                original.getMaxRequestSize() < 0
                        ? -1
                        : Math.max(original.getMaxRequestSize(), Math.addExact(fileBytes, 1048576));
        return new MultipartConfigElement(
                original.getLocation(), fileBytes, requestBytes, original.getFileSizeThreshold());
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> multipartRequestLimitCustomizer(
            MultipartConfigElement multipartConfig) {
        long maxRequestSize = multipartConfig.getMaxRequestSize();
        int maxPostSize = maxRequestSize < 0
                ? -1
                : (int) Math.min(maxRequestSize, Integer.MAX_VALUE);
        return factory -> factory.addConnectorCustomizers(connector -> connector.setMaxPostSize(maxPostSize));
    }
}
