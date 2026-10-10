package com.shardvault.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ShardVaultApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShardVaultApiApplication.class, args);
    }
}
