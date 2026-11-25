package vn.edu.fpt.paspm.catalog.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

/** The same V27 assertions as {@link StoreMigrationV27Test}, on a disposable MySQL 8.4 schema. */
@EnabledIfSystemProperty(named = "paspm.test.mysql", matches = "true")
class StoreMigrationV27MySqlTest {
    @Test void v27BackfillsUniqueSlugsOnDuplicateNamesAndAddsTheProfileColumnsOnMySql() {
        var db = MySqlTestDatabase.create();
        StoreMigrationV27Test.verify(db.url(), db.user(), db.password());
    }
}
