-- V1__create_users_table.sql
-- Initial users table for authentication & authorization

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- Login credentials
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,          -- bcrypt or argon2 hash only!
    
    -- Basic profile
    full_name VARCHAR(100),
    phone_number VARCHAR(20),
    profile_picture_url VARCHAR(512),
    
    -- Status & security
    is_active BOOLEAN DEFAULT TRUE,
    is_locked BOOLEAN DEFAULT FALSE,
    failed_login_attempts INT DEFAULT 0,
    last_login_at TIMESTAMP NULL,
    
    -- Roles (simple string array – later can be normalized to roles table)
    roles JSON NOT NULL DEFAULT ('["USER"]'),     -- e.g. ["USER"], ["USER","ADMIN"]
    
    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL                     -- for soft deletes
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for fast lookups
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_active ON users(is_active);