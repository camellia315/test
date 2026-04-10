package com.campus.lostfound;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.campus")
@MapperScan("com.campus.lostfound.mapper")
public class LostFoundServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LostFoundServiceApplication.class, args);
    }
}
