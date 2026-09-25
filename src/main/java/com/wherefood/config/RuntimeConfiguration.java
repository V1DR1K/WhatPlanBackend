package com.wherefood.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RuntimeProperties.class)
public class RuntimeConfiguration {
}
