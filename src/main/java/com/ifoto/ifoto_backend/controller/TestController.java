package com.ifoto.ifoto_backend.controller;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/dbtest")
    public String dbTest() {
        try (Connection conn = dataSource.getConnection()) {
            return "DB connection successful: " + conn.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            return "DB connection failed: " + e.getMessage();
        }
    }
}
