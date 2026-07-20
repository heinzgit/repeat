-- 错题集管理系统数据库脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS wrongbook CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wrongbook;

-- 错题表
CREATE TABLE IF NOT EXISTS wrong_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    grade VARCHAR(50),
    subject VARCHAR(50),
    source VARCHAR(100),
    question_no VARCHAR(50),
    category VARCHAR(20),
    wrong_date DATE,
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 重做记录表
CREATE TABLE IF NOT EXISTS retry_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wrong_question_id BIGINT NOT NULL,
    retry_date DATE,
    result VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (wrong_question_id) REFERENCES wrong_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
