package com.yugabyte.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
public class RagApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagApiApplication.class, args);
    }
}

