package com.wherefood;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class WhereFoodApplication {
 public static void main(String[] args) { SpringApplication.run(WhereFoodApplication.class, args); }
}
