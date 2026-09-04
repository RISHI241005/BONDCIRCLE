-- ============================================================
-- Local MySQL Database & User Initialization Script
-- Run this in MySQL Workbench, MySQL Command Line Client, or DBeaver:
-- ============================================================

CREATE DATABASE IF NOT EXISTS dating_chat_db 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'chat_user'@'localhost' IDENTIFIED BY 'change_me_for_local_development';
CREATE USER IF NOT EXISTS 'chat_user'@'%' IDENTIFIED BY 'change_me_for_local_development';

GRANT ALL PRIVILEGES ON dating_chat_db.* TO 'chat_user'@'localhost';
GRANT ALL PRIVILEGES ON dating_chat_db.* TO 'chat_user'@'%';

FLUSH PRIVILEGES;

-- Verification
SHOW DATABASES LIKE 'dating_chat_db';
