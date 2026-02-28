package com.news.publish;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class PublishCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(PublishCenterApplication.class, args);
    }
}
