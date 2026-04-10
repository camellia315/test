package com.campus.market;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.campus")
@MapperScan("com.campus.market.mapper")
public class MarketServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketServiceApplication.class, args);
    }
}
