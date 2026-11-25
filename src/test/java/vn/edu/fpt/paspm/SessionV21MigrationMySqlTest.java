package vn.edu.fpt.paspm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
class SessionV21MigrationMySqlTest {
    @Test void populatedPublishedV20UpgradesWithoutChangingHistory() throws Exception {
        var db=MySqlTestDatabase.create();
        SessionV21MigrationTest.verify(db.url(),db.user(),db.password());
    }
}
