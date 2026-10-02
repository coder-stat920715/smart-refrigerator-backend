package com.smartfridge.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Boots the full application against a real PostgreSQL.
 *
 * Database selection (first match wins):
 *   1. Testcontainers PostgreSQL, when Docker is available.
 *   2. A local PostgreSQL, configured through system properties or environment variables:
 *        test.db.url       / TEST_DB_URL       (default jdbc:postgresql://localhost:5432/smartfridge_test)
 *        test.db.username  / TEST_DB_USERNAME  (default postgres)
 *        test.db.password  / TEST_DB_PASSWORD  (default postgres)
 *      The database must already exist. Its tables are TRUNCATED before every test,
 *      so never point this at a database that holds data you care about.
 *   3. Neither available: the integration tests are skipped (not failed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static PostgreSQLContainer<?> container;
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static boolean databaseAvailable;
    private static String unavailableReason = "";

    static {
        if (isDockerAvailable()) {
            try {
                container = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("smartfridge_test")
                        .withUsername("test")
                        .withPassword("test");
                container.start();
                jdbcUrl = container.getJdbcUrl();
                username = container.getUsername();
                password = container.getPassword();
                databaseAvailable = true;
                System.out.println("[tests] Using Testcontainers PostgreSQL at " + jdbcUrl);
            } catch (Exception ex) {
                unavailableReason = "Testcontainers failed to start PostgreSQL: " + ex.getMessage();
                container = null;
            }
        } else {
            unavailableReason = "Docker is not available.";
        }

        if (!databaseAvailable) {
            jdbcUrl = setting("test.db.url", "TEST_DB_URL", "jdbc:postgresql://localhost:5432/smartfridge_test");
            username = setting("test.db.username", "TEST_DB_USERNAME", "postgres");
            password = setting("test.db.password", "TEST_DB_PASSWORD", "postgres");
            try (Connection ignored = DriverManager.getConnection(jdbcUrl, username, password)) {
                databaseAvailable = true;
                System.out.println("[tests] Docker unavailable, using local PostgreSQL at " + jdbcUrl);
            } catch (Exception ex) {
                unavailableReason += " Local PostgreSQL at " + jdbcUrl + " is not reachable: " + ex.getMessage()
                        + ". Start Docker Desktop, or create the database 'smartfridge_test' on a local PostgreSQL "
                        + "(see TEST_DB_URL / TEST_DB_USERNAME / TEST_DB_PASSWORD).";
            }
        }
    }

    @BeforeAll
    static void requireDatabase() {
        assumeTrue(databaseAvailable, "Integration tests skipped. " + unavailableReason);
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE ota_update_jobs, firmware_packages, fault_events, "
                + "telemetry_logs, devices RESTART IDENTITY CASCADE");
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            return false;
        }
    }

    private static String setting(String property, String envName, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }
}