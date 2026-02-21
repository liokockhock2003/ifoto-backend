package com.ifoto.ifoto_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/photos")
@CrossOrigin(origins = "http://localhost:5173")
public class PhotoController {

    @Autowired
    private DataSource dataSource;

    // Root endpoint: GET /api/photos
    // In PhotoController.java
    @GetMapping
    public Map<String, Object> getPhotos() {
        // Later: fetch from DB via repository
        List<String> dummyPhotos = List.of(
                "photo1.jpg - Sunset at Balakong",
                "photo2.jpg - iFoto logo concept",
                "photo3.jpg - User upload example");
        return Map.of(
                "status", "success",
                "photos", dummyPhotos,
                "count", dummyPhotos.size());
    }

    // Your test endpoint: GET /api/photos/test
    @GetMapping("/test")
    public Map<String, String> testDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return Map.of(
                    "status", valid ? "success" : "invalid",
                    "message", valid ? "Database connection is valid!" : "Database connection is not valid.");
        } catch (SQLException e) {
            e.printStackTrace();
            return Map.of(
                    "status", "error",
                    "message", "Error connecting to the database: " + e.getMessage());
        }
    }

    // Add this later for real photos
    // @GetMapping
    // public List<Photo> getAllPhotos() { ... }
}