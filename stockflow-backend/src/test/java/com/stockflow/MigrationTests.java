package com.stockflow;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class MigrationTests {
    private static final List<String> TABLES = List.of("app_users", "categories", "suppliers", "customers",
        "products", "inventory_transactions", "purchase_orders", "purchase_order_items", "sales_orders",
        "sales_order_items", "stock_documents", "stock_document_lines");
    private String database() { return "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"; }
    private Flyway flyway(String url, boolean baseline) {
        return Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
            .baselineOnMigrate(baseline).baselineVersion("1").cleanDisabled(true).load();
    }
    private void legacy(String url, boolean v11) throws Exception {
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__initial_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/fixtures/v1_data.sql"));
            if (v11) try (var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE app_users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE");
                statement.execute("UPDATE app_users SET active=FALSE WHERE id=1");
            }
        }
    }
    private Map<String, List<String>> snapshot(String url) throws Exception {
        var snapshot = new TreeMap<String, List<String>>();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            for (String table : TABLES) {
                var values = new ArrayList<String>();
                try (var rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    while (rows.next()) for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                        if (table.equals("app_users") && rows.getMetaData().getColumnName(column).equalsIgnoreCase("active")) continue;
                        values.add(rows.getMetaData().getColumnName(column) + "=" + rows.getString(column));
                    }
                }
                snapshot.put(table, values);
            }
        }
        return snapshot;
    }
    private boolean active(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT active FROM app_users WHERE id=1")) {
            assertTrue(result.next()); return result.getBoolean(1);
        }
    }
    @Test void emptyDatabaseInitializesAndSecondMigrationDoesNothing() throws Exception {
        String url = database(); var flyway = flyway(url, false);
        assertEquals(2, flyway.migrate().migrationsExecuted);
        assertEquals("2", flyway.info().current().getVersion().toString());
        assertEquals(12, snapshot(url).size());
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertThrows(FlywayException.class, flyway::clean);
    }
    @Test void originalV1UpgradePreservesEveryTableAndActivatesExistingUsers() throws Exception {
        String url = database(); legacy(url, false); var before = snapshot(url);
        assertEquals(1, flyway(url, true).migrate().migrationsExecuted);
        assertEquals(before, snapshot(url)); assertTrue(active(url));
    }
    @Test void v11UpgradePreservesInactiveUsersAndAllData() throws Exception {
        String url = database(); legacy(url, true); var before = snapshot(url);
        assertEquals(1, flyway(url, true).migrate().migrationsExecuted);
        assertEquals(before, snapshot(url)); assertFalse(active(url));
        assertEquals(0, flyway(url, false).migrate().migrationsExecuted);
        assertFalse(active(url));
    }
    @Test void nonemptyDatabaseRequiresExplicitAdoption() throws Exception {
        String url = database(); legacy(url, false); var before = snapshot(url);
        assertThrows(FlywayException.class, () -> flyway(url, false).migrate());
        assertEquals(before, snapshot(url));
    }
}
