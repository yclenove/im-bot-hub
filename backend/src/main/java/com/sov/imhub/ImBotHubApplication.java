package com.sov.imhub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sov.imhub.mapper")
public class ImBotHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImBotHubApplication.class, args);
    }
}
