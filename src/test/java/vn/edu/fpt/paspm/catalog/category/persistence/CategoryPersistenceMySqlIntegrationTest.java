package vn.edu.fpt.paspm.catalog.category.persistence;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
class CategoryPersistenceMySqlIntegrationTest extends CategoryPersistenceIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){MySqlTestDatabase.create().register(registry);}
}
