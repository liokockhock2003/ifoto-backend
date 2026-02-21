package com.ifoto.ifoto_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

@RestController
public class HelloController {

    @GetMapping("/")
    @Value("${app.welcome.message:Default message}")
    public String hello(@Value("${app.welcome.message:Default message}") String message) {
        return message;
    }
}
