package com.sapio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SapioApplication {

    public static void main(String[] args) {
        SpringApplication.run(SapioApplication.class, args);
    }
}
