package db.migration;

import java.sql.PreparedStatement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Test-only fixture required by V45's historical Tomás/Avril backfill. */
public class V44_5__seed_legacy_members_for_couple_migration extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (PreparedStatement statement = context.getConnection().prepareStatement(
                "insert into users(username, role) values (?, 'USER') on conflict (username) do nothing")) {
            statement.setString(1, "tomas");
            statement.addBatch();
            statement.setString(1, "avril");
            statement.addBatch();
            statement.executeBatch();
        }
    }
}
