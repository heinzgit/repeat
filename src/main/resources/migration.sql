-- 升级脚本(已有数据库时使用)

ALTER TABLE wrong_question ADD COLUMN answer_text TEXT;

CREATE TABLE IF NOT EXISTS wrong_question_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wrong_question_id BIGINT NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    original_name VARCHAR(255),
    stored_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    size_bytes BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (wrong_question_id) REFERENCES wrong_question(id) ON DELETE CASCADE,
    INDEX idx_wrong_question_id (wrong_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
