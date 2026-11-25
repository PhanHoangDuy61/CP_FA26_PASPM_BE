package vn.edu.fpt.paspm.catalog.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

/** The same V28 assertions as {@link ProductSoftDeleteV28MigrationTest}, on a disposable MySQL 8.4 schema. */
@EnabledIfSystemProperty(named = "paspm.test.mysql", matches = "true")
class ProductSoftDeleteV28MySqlTest {
    @Test void v28AddsSoftDeleteColumnsWithoutTouchingExistingProductsOnMySql() {
        var db = MySqlTestDatabase.create();
        ProductSoftDeleteV28MigrationTest.verify(db.url(), db.user(), db.password());
    }
}
