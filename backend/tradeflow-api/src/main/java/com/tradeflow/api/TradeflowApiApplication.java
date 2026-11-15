package com.tradeflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TradeflowApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeflowApiApplication.class, args);
	}

}
