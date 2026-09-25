package com.datingapp.chat.database;

import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseSchemaIntegrityMySQLTest extends AbstractMySQLIntegrationTest {

    @Test
    @DisplayName("Should verify all 5 core domain tables exist in MySQL schema")
    void testTablesExistInMySQL() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'dating_chat_test_db'",
                String.class
        );

        assertTrue(tables.contains("conversations"));
        assertTrue(tables.contains("conversation_participants"));
        assertTrue(tables.contains("messages"));
        assertTrue(tables.contains("blocks"));
        assertTrue(tables.contains("reports"));
    }

    @Test
    @DisplayName("Should enforce MySQL foreign key constraint on orphaned message insert")
    void testForeignKeyConstraintEnforcement() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO messages (public_id, conversation_id, sender_id, message_type, status, content, created_at, updated_at) " +
                    "VALUES ('orphaned-msg-uuid', 999999, 101, 'TEXT', 'SENT', 'Orphan message', NOW(3), NOW(3))"
            );
        });
    }

    @Test
    @DisplayName("Should enforce unique constraint on duplicate conversation participants in MySQL")
    void testUniqueParticipantConstraint() {
        // Insert conversation
        jdbcTemplate.update(
                "INSERT INTO conversations (id, public_id, status, created_at, updated_at) " +
                "VALUES (100, 'conv-unique-test', 'ACTIVE', NOW(3), NOW(3))"
        );

        // Insert first participant (100, 101)
        jdbcTemplate.update(
                "INSERT INTO conversation_participants (conversation_id, user_id, joined_at, updated_at) " +
                "VALUES (100, 101, NOW(3), NOW(3))"
        );

        // Duplicate participant (100, 101) must fail
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO conversation_participants (conversation_id, user_id, joined_at, updated_at) " +
                    "VALUES (100, 101, NOW(3), NOW(3))"
            );
        });
    }

    @Test
    @DisplayName("Should enforce MySQL check constraint preventing self-blocking")
    void testSelfBlockCheckConstraint() {
        assertThrows(org.springframework.dao.DataAccessException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO blocks (blocker_user_id, blocked_user_id, created_at) " +
                    "VALUES (101, 101, NOW(3))"
            );
        });
    }
}
