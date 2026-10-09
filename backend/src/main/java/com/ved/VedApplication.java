package com.ved;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VedApplication {

    public static void main(String[] args) {
        SpringApplication.run(VedApplication.class, args);
    }
}
