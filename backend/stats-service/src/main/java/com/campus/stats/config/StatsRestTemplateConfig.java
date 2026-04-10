package com.campus.stats.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class StatsRestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate statsRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(1200))
                .setReadTimeout(Duration.ofMillis(1200))
                .build();
    }
}

