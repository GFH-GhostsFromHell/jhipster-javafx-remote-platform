package com.gfh.industrial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class IndustrialStudioApplication {
    public static void main(String[] args) {
        SpringApplication.run(IndustrialStudioApplication.class, args);
    }
}
