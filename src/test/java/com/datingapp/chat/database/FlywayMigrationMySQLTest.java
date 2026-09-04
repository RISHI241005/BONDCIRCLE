package com.datingapp.chat.database;

import com.datingapp.chat.testutil.AbstractMySQLIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationMySQLTest extends AbstractMySQLIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Should successfully apply all Flyway migrations V0 through V5 to MySQL")
    void testAllFlywayMigrationsApplied() {
        MigrationInfo[] appliedMigrations = flyway.info().applied();
        assertNotNull(appliedMigrations);
        assertTrue(appliedMigrations.length >= 6, "Expected at least 6 migrations (V0 through V5)");

        List<String> scriptNames = Arrays.stream(appliedMigrations)
                .map(MigrationInfo::getScript)
                .toList();

        assertTrue(scriptNames.contains("V0__schema_initialization.sql"));
        assertTrue(scriptNames.contains("V1__create_conversations.sql"));
        assertTrue(scriptNames.contains("V2__create_conversation_participants.sql"));
        assertTrue(scriptNames.contains("V3__create_messages.sql"));
        assertTrue(scriptNames.contains("V4__create_blocks.sql"));
        assertTrue(scriptNames.contains("V5__create_reports.sql"));

        for (MigrationInfo info : appliedMigrations) {
            assertEquals("SUCCESS", info.getState().name(),
                    "Migration " + info.getScript() + " should be in SUCCESS state");
        }
    }
}
