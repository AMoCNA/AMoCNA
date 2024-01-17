package com.example.droolsprototype;

import com.example.droolsprototype.demo.DemoTaskRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Application entry point
 */
@SpringBootApplication
public class DroolsPrototypeApplication {

    public static void main(String[] args) {
        //create an instance of this class and all its dependencies
        ConfigurableApplicationContext context = SpringApplication.run(DroolsPrototypeApplication.class, args);
        //get the instance and invoke the run() method
        context.getBean(DemoTaskRunner.class).run();
    }
}
