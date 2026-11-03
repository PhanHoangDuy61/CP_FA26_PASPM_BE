package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:utilityfixture;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=","paspm.bootstrap-password=",
    "paspm.utility.fixture-analysis-date=2026-10-17","paspm.utility.fixture-password=FixtureTestOnly123!"
})
@ActiveProfiles({"dev","utility-fixture"})
class UtilityFixtureTest {
    @Autowired UtilityFixtureSource source;
    @Autowired JdbcTemplate jdbc;
    @Test void explicitFixtureProfilesProvideStoreIsolatedIdempotentData() throws Exception {
        int store=jdbc.queryForObject("SELECT store_id FROM store WHERE name='F03 Fixture Store A'",Integer.class);
        var transactions=source.load(store,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31));
        assertEquals(3,transactions.size());assertTrue(transactions.stream().allMatch(t->t.storeId()==store));
        assertEquals("DEV_FIXTURE",source.sourceType());
        assertTrue(source.load(2147483000,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)).isEmpty());
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch",Integer.class);
        source.run(new DefaultApplicationArguments());
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch",Integer.class));
    }
    @Test void diagnosticDateRangesProvideMissingExpiryNegativeUtilityAndDuplicateItems() {
        int store=jdbc.queryForObject("SELECT store_id FROM store WHERE name='F03 Fixture Store A'",Integer.class);
        LocalDate analysis=LocalDate.of(2026,10,17);
        for(int years:new int[]{1,2,3}) {
            LocalDate day=analysis.minusYears(years);
            var transactions=source.load(store,day,day);
            assertEquals(1,transactions.size());
            if(years==1) {
                int product=transactions.get(0).items().get(0).productId();
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch WHERE product_id=? AND expiry_date IS NULL",Integer.class,product));
            }
            if(years==2)assertTrue(transactions.get(0).items().get(0).unitPrice().compareTo(transactions.get(0).items().get(0).unitCost())<0);
            if(years==3)assertEquals(2,transactions.get(0).items().size());
        }
    }
}
