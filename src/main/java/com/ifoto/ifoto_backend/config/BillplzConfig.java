package com.ifoto.ifoto_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Configuration
@ConfigurationProperties(prefix = "billplz")
@Getter
@Setter
public class BillplzConfig {

    private String apiKey;
    private String collectionId;
    private String xSignatureKey;
    private String baseUrl;
    private String callbackUrl;
    private String redirectUrl;

    @Bean
    public RestClient billplzRestClient() {
        String credentials = Base64.getEncoder().encodeToString((apiKey + ":").getBytes());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + credentials)
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
    }
}
