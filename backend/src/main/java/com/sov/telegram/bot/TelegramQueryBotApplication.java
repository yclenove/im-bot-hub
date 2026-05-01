package com.sov.telegram.bot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sov.telegram.bot.mapper")
public class TelegramQueryBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelegramQueryBotApplication.class, args);
    }
}
