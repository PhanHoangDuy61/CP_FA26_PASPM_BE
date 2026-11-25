package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import vn.edu.fpt.paspm.storefront.StoreSlug;

public class V29__f09_storefront extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE store ADD COLUMN store_slug VARCHAR(180) NULL");
            statement.execute("ALTER TABLE store ADD COLUMN storefront_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        }
        try (Statement query = context.getConnection().createStatement();
             ResultSet stores = query.executeQuery("SELECT store_id,name FROM store ORDER BY store_id");
             PreparedStatement update = context.getConnection().prepareStatement(
                     "UPDATE store SET store_slug=? WHERE store_id=?")) {
            while (stores.next()) {
                update.setString(1, StoreSlug.generate(stores.getString("name"), stores.getInt("store_id")));
                update.setInt(2, stores.getInt("store_id"));
                update.addBatch();
            }
            update.executeBatch();
        }
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("CREATE UNIQUE INDEX uq_store_store_slug ON store(store_slug)");
            statement.execute("ALTER TABLE product ADD COLUMN image_url VARCHAR(2048) NULL");
            statement.execute("ALTER TABLE product ADD COLUMN description TEXT NULL");
        }
    }
}
