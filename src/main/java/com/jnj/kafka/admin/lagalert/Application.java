package com.jnj.kafka.admin.lagalert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.support.GenericApplicationContext;

@SpringBootApplication
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public Application(GenericApplicationContext context) {
        // gracefully handles ctrl-c kills of application
        Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook") {
            @Override
            public void run() {
                log.info("Application shutdown hook called");
                context.close();
            }
        });
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args).close();
    }
}
