package com.tenxengage.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableCaching
@EnableJpaAuditing
public class TenxengageAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenxengageAdminApplication.class, args);
    }
}
