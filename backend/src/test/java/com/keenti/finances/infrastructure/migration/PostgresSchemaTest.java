package com.keenti.finances.infrastructure.migration;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PostgresSchemaTest {

    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("V(\\d+)__.+\\.sql");

    @Inject
    Flyway flyway;

    @Inject
    DataSource dataSource;

    @Test
    void schemaComesFromEveryFlywayMigrationOnPostgres() throws Exception {
        assertPostgresDatabase();

        List<Integer> migrationVersions = findVersionedMigrations();
        long appliedVersionCount = Arrays.stream(flyway.info().applied())
                .filter(migration -> migration.getVersion() != null)
                .count();

        assertEquals(migrationVersions.size(), appliedVersionCount,
                "every versioned migration file should be recorded as applied");
        assertEquals(String.valueOf(Collections.max(migrationVersions)),
                flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful,
                "the applied schema should pass Flyway validation");

        assertPaymentIdempotencyIndexes();
    }

    private void assertPostgresDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 18,
                    "integration tests should match the production PostgreSQL major version");
        }
    }

    private static List<Integer> findVersionedMigrations() throws IOException, URISyntaxException {
        URL migrationDirectory = Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader().getResource("db/migration"),
                "migration resource directory");
        Path path = Path.of(migrationDirectory.toURI());
        List<Integer> versions = new ArrayList<>();
        try (var files = Files.list(path)) {
            for (String name : files
                    .filter(Files::isRegularFile)
                    .map(file -> file.getFileName().toString())
                    .toList()) {
                var matcher = VERSIONED_MIGRATION.matcher(name);
                if (matcher.matches()) {
                    versions.add(Integer.parseInt(matcher.group(1)));
                }
            }
        }
        return versions;
    }

    private void assertPaymentIdempotencyIndexes() throws SQLException {
        Set<String> expectedIndexes = Set.of(
                "payment_record_personal_period_uq",
                "payment_record_shared_period_member_uq");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery("""
                     SELECT indexname, indexdef
                     FROM pg_indexes
                     WHERE schemaname = current_schema()
                       AND tablename = 'payment_record'
                       AND indexname IN (
                           'payment_record_personal_period_uq',
                           'payment_record_shared_period_member_uq'
                       )
                     """)) {
            var definitions = new HashMap<String, String>();
            while (indexes.next()) {
                definitions.put(indexes.getString("indexname"), indexes.getString("indexdef"));
            }

            assertEquals(expectedIndexes, definitions.keySet());
            assertTrue(definitions.values().stream().allMatch(sql -> sql.contains("UNIQUE")));
            assertTrue(definitions.values().stream().allMatch(sql -> sql.contains("WHERE")));
            assertTrue(definitions.get("payment_record_personal_period_uq")
                    .contains("member_id IS NULL"));
            assertTrue(definitions.get("payment_record_shared_period_member_uq")
                    .contains("member_id IS NOT NULL"));
        }
    }
}
