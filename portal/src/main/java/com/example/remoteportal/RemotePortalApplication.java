package com.example.remoteportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RemotePortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(RemotePortalApplication.class, args);
    }
}
