package com.brosna.expensebot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ExpenseBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseBotApplication.class, args);
    }
}
