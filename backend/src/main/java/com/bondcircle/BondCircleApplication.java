package com.bondcircle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.bondcircle", "com.datingapp.chat"})
@EntityScan(basePackages = {"com.bondcircle", "com.datingapp.chat"})
@EnableJpaRepositories(basePackages = {"com.bondcircle", "com.datingapp.chat"})
public class BondCircleApplication {

    public static void main(String[] args) {
        SpringApplication.run(BondCircleApplication.class, args);
    }
}
