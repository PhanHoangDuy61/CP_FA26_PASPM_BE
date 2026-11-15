package vn.edu.fpt.paspm.utility;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
class VersionedUtilityPersistenceMySqlTest extends VersionedUtilityPersistenceTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        var db=MySqlTestDatabase.create();
        registry.add("spring.datasource.url",db::url);
        registry.add("spring.datasource.username",db::user);
        registry.add("spring.datasource.password",db::password);
    }
}
