package com.datingapp.chat.testutil;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base integration test class running against a real MySQL 8+ database instance.
 * Automatically manages schema migration verification and deterministic table cleanup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractMySQLIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureMySQLProperties(DynamicPropertyRegistry registry) {
        // Points to the dedicated real MySQL 8 test database on localhost
        registry.add("spring.datasource.url", () ->
                "jdbc:mysql://localhost:3306/dating_chat_test_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> "chat_user");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @BeforeEach
    void cleanDatabase() {
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
            jdbcTemplate.execute("TRUNCATE TABLE reports;");
            jdbcTemplate.execute("TRUNCATE TABLE blocks;");
            jdbcTemplate.execute("TRUNCATE TABLE messages;");
            jdbcTemplate.execute("TRUNCATE TABLE conversation_participants;");
            jdbcTemplate.execute("TRUNCATE TABLE conversations;");
            jdbcTemplate.execute("TRUNCATE TABLE users;");
            jdbcTemplate.update("INSERT INTO users (id, full_name, phone, email, password_hash, created_at, status) VALUES (?, ?, ?, ?, ?, NOW(3), 'ACTIVE')",
                    UserTestFactory.USER_A, "Alex Rivera", "+1234567890", "alex@example.test", "test-only");
            jdbcTemplate.update("INSERT INTO users (id, full_name, phone, email, password_hash, created_at, status) VALUES (?, ?, ?, ?, ?, NOW(3), 'ACTIVE')",
                    UserTestFactory.USER_B, "Blair Morgan", "+1234567891", "blair@example.test", "test-only");
            jdbcTemplate.update("INSERT INTO users (id, full_name, phone, email, password_hash, created_at, status) VALUES (?, ?, ?, ?, ?, NOW(3), 'ACTIVE')",
                    UserTestFactory.USER_C, "Casey Shah", "+1234567892", "casey@example.test", "test-only");
            jdbcTemplate.update("INSERT INTO users (id, full_name, phone, email, password_hash, created_at, status) VALUES (?, ?, ?, ?, ?, NOW(3), 'ACTIVE')",
                    UserTestFactory.BLOCKED_USER, "Drew Patel", "+1234567893", "drew@example.test", "test-only");
            jdbcTemplate.update("INSERT INTO users (id, full_name, phone, email, password_hash, created_at, status) VALUES (?, ?, ?, ?, ?, NOW(3), 'ACTIVE')",
                    UserTestFactory.ADMIN_USER, "Admin User", "+1234567894", "admin@example.test", "test-only");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1;");
        }
    }
}
