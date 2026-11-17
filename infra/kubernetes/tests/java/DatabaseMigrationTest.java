import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseMigrationTest {
    @TempDir Path directory;

    private Process run(boolean configured) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = directory + System.getProperty("path.separator")
                + System.getProperty("java.class.path");
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", classpath, "DatabaseMigration")
                .redirectErrorStream(true);
        builder.environment().remove("SPRING_DATASOURCE_URL");
        builder.environment().remove("SPRING_DATASOURCE_USERNAME");
        builder.environment().remove("SPRING_DATASOURCE_PASSWORD");
        if (configured) {
            builder.environment().put("SPRING_DATASOURCE_URL", databaseUrl());
            builder.environment().put("SPRING_DATASOURCE_USERNAME", "owner");
            builder.environment().put("SPRING_DATASOURCE_PASSWORD", "test-only-password");
        }
        Process process = builder.start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Migration process must exit without starting application listeners");
        }
        return process;
    }

    private String databaseUrl() {
        return "jdbc:h2:file:" + directory.resolve("database").toString().replace('\\', '/');
    }

    private void migration(String name, String sql) throws Exception {
        Path location = Files.createDirectories(directory.resolve("db/migration"));
        Files.writeString(location.resolve(name), sql);
    }

    @Test void freshDatabaseAndRepeatedRunSucceed() throws Exception {
        migration("V1__create_test.sql", "CREATE TABLE delivery (id INT PRIMARY KEY);");
        assertEquals(0, run(true).exitValue());
        assertEquals(0, run(true).exitValue());
        try (var connection = DriverManager.getConnection(databaseUrl(), "owner", "test-only-password");
             var rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE AND \"version\" = '1'")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    @Test void invalidMigrationFailsAndRedactsOutput() throws Exception {
        migration("V1__create_test.sql", "CREATE TABLE delivery (id INT PRIMARY KEY);");
        assertEquals(0, run(true).exitValue());
        migration("V2__invalid.sql", "THIS IS INVALID SQL 'test-only-password';");
        Process failed = run(true);
        assertEquals(1, failed.exitValue());
        String output = new String(failed.getInputStream().readAllBytes());
        assertTrue(output.contains("Database migration failed"));
        assertFalse(output.contains("test-only-password"));
    }

    @Test void missingConfigurationFails() throws Exception {
        assertEquals(1, run(false).exitValue());
    }

    @Test void missingMigrationScriptsFail() throws Exception {
        assertEquals(1, run(true).exitValue());
    }
}
