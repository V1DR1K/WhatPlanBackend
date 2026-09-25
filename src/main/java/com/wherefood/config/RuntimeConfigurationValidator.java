package com.wherefood.config;

import org.springframework.stereotype.Component;

@Component
public class RuntimeConfigurationValidator {
    public RuntimeConfigurationValidator(RuntimeProperties properties) {
        properties.validate();
    }
}
