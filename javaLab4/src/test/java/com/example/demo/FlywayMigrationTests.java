package com.example.demo;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationTests {
    private DriverManagerDataSource database() {
        return new DriverManagerDataSource("jdbc:h2:mem:migration_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void legacyCore(DriverManagerDataSource source) {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__create_routing_schema.sql"))
                .execute(source);
        new JdbcTemplate(source).update("""
                insert into customers(name,email,phone,address,latitude,longitude)
                values ('Existing customer','existing@example.com','0700000000','Existing address',44,26)
                """);
    }

    @Test
    void freshSchemaMigratesAndSubsequentStartupDoesNotRepeatChanges() {
        var source = database();
        Flyway flyway = Flyway.configure().dataSource(source).load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        assertEquals("2", flyway.info().current().getVersion().toString());
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        var jdbc = new JdbcTemplate(source);
        assertEquals(0L, jdbc.queryForObject("select count(*) from scenario_vehicles", Long.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("insert into scenarios(name) values (' ')"));
    }

    @Test
    void existingCoreIsPreservedWhenExplicitlyBaselinedAtVersionOne() {
        var source = database();
        legacyCore(source);
        Flyway flyway = Flyway.configure().dataSource(source)
                .baselineOnMigrate(true).baselineVersion("1").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals("existing@example.com", new JdbcTemplate(source)
                .queryForObject("select email from customers", String.class));
        assertEquals("2", flyway.info().current().getVersion().toString());
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void existingHomeworkSchemaCanBeBaselinedAtVersionTwo() {
        var source = database();
        legacyCore(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__add_scenarios.sql"))
                .execute(source);
        var jdbc = new JdbcTemplate(source);
        jdbc.update("insert into scenarios(name) values ('Existing scenario')");
        Flyway flyway = Flyway.configure().dataSource(source)
                .baselineOnMigrate(true).baselineVersion("2").load();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("Existing scenario", jdbc.queryForObject("select name from scenarios", String.class));
        assertEquals(1L, jdbc.queryForObject("select count(*) from customers", Long.class));
    }

    @Test
    void untrackedNonemptyDatabaseRequiresExplicitAdoption() {
        var source = database();
        legacyCore(source);
        assertThrows(FlywayException.class, () -> Flyway.configure().dataSource(source).load().migrate());
        assertEquals(1L, new JdbcTemplate(source).queryForObject("select count(*) from customers", Long.class));
    }

    @Test
    void changingAnAppliedMigrationFailsValidation(@TempDir Path directory) throws Exception {
        var source = database();
        Flyway.configure().dataSource(source).load().migrate();
        for (String file : new String[]{"V1__create_routing_schema.sql", "V2__add_scenarios.sql"}) {
            String sql;
            try (var input = new ClassPathResource("db/migration/" + file).getInputStream()) {
                sql = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            Files.writeString(directory.resolve(file), sql.replace("VARCHAR(100)", "VARCHAR(101)"));
        }
        Flyway modified = Flyway.configure().dataSource(source)
                .locations("filesystem:" + directory).load();
        assertFalse(modified.validateWithResult().validationSuccessful);
        assertThrows(FlywayException.class, modified::migrate);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_DB_URL", matches = ".+")
    void migrationsAndLegacyAdoptionWorkOnRealPostgresql() {
        var source = new DriverManagerDataSource(System.getenv("MIGRATION_TEST_DB_URL"),
                System.getenv("MIGRATION_TEST_DB_USERNAME"), System.getenv("MIGRATION_TEST_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        String fresh = "migration_fresh_" + UUID.randomUUID().toString().replace("-", "");
        String legacy = "migration_legacy_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + fresh);
        jdbc.execute("create schema " + legacy);
        try {
            Flyway flyway = Flyway.configure().dataSource(source).schemas(fresh).defaultSchema(fresh).load();
            assertEquals(2, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertTrue(flyway.validateWithResult().validationSuccessful);
            Flyway.configure().dataSource(source).schemas(legacy).defaultSchema(legacy).target("1").load().migrate();
            jdbc.execute("drop table " + legacy + ".flyway_schema_history");
            jdbc.update("insert into " + legacy + ".customers(name,email,phone,address,latitude,longitude) "
                    + "values ('Existing','pg-existing@example.com','0700000000','Address',44,26)");
            Flyway adopted = Flyway.configure().dataSource(source).schemas(legacy).defaultSchema(legacy)
                    .baselineOnMigrate(true).baselineVersion("1").load();
            assertEquals(1, adopted.migrate().migrationsExecuted);
            assertEquals("pg-existing@example.com", jdbc.queryForObject(
                    "select email from " + legacy + ".customers", String.class));
            assertEquals(0L, jdbc.queryForObject("select count(*) from " + legacy + ".scenarios", Long.class));
        } finally {
            jdbc.execute("drop schema " + fresh + " cascade");
            jdbc.execute("drop schema " + legacy + " cascade");
        }
    }
}
