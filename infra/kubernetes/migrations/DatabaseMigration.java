import org.flywaydb.core.Flyway;

/** Runs only the migrations packaged in the owning service image. No Spring context is started. */
public final class DatabaseMigration {
    private DatabaseMigration() {}

    public static void main(String[] args) {
        // Do not emit exception messages: JDBC driver errors can include connection credentials.
        try {
            Flyway.configure()
                    .loggers(new String[0])
                    .dataSource(required("SPRING_DATASOURCE_URL"),
                            required("SPRING_DATASOURCE_USERNAME"),
                            required("SPRING_DATASOURCE_PASSWORD"))
                    .locations("classpath:db/migration")
                    .failOnMissingLocations(true)
                    .validateMigrationNaming(true)
                    .cleanDisabled(true)
                    .load()
                    .migrate();
            System.out.println("Database migrations completed.");
        } catch (Exception | LinkageError failure) {
            System.err.println("Database migration failed; inspect database availability and migration history.");
            System.exit(1);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing migration configuration");
        }
        return value;
    }
}
