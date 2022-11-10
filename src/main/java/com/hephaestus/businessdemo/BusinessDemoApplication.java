package com.hephaestus.businessdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BusinessDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessDemoApplication.class, args);
    }

}
