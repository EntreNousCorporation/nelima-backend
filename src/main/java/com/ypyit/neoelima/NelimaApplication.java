package com.ypyit.neoelima;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableCaching
@SpringBootApplication
@EnableAspectJAutoProxy
public class NelimaApplication {

    public static void main(String[] args) {
        SpringApplication.run(NelimaApplication.class, args);
    }
}
