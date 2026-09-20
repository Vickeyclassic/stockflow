package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Handles both original V1 users and databases already upgraded to V1.1. */
public class V2__user_activation extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "%", "%")) {
            while (columns.next()) {
                if ("app_users".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && "active".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return;
            }
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE app_users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE");
        }
    }
}
