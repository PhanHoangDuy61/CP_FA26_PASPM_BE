package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
class MiningDispatchLeaseMySqlTest {
    @Test void independentClientsCompeteAndExpiredOwnerIsFenced() throws Exception {
        var db=MySqlTestDatabase.create();
        var a=new DriverManagerDataSource(db.url(),db.user(),db.password());
        var b=new DriverManagerDataSource(db.url(),db.user(),db.password());
        var jdbc=new JdbcTemplate(a);
        jdbc.execute("CREATE TABLE mining_request(request_id BIGINT PRIMARY KEY,store_id INT NOT NULL,status VARCHAR(12) NOT NULL,lease_owner VARCHAR(36),lease_fence BIGINT NOT NULL DEFAULT 0,lease_until TIMESTAMP(6) NULL)");
        jdbc.execute("CREATE TABLE output_rows(request_id BIGINT NOT NULL,output_value VARCHAR(20))");
        jdbc.update("INSERT INTO mining_request(request_id,store_id,status) VALUES(10,1,'PENDING')");
        var first=new MiningDispatchLease(jdbc,new DataSourceTransactionManager(a));
        var jdbcB=new JdbcTemplate(b);
        var second=new MiningDispatchLease(jdbcB,new DataSourceTransactionManager(b));
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var p=pool.submit(()->{gate.await();return first.claim(1,10,"instance-a",60);});
            var q=pool.submit(()->{gate.await();return second.claim(1,10,"instance-b",60);});
            gate.countDown();var x=p.get(10,TimeUnit.SECONDS);var y=q.get(10,TimeUnit.SECONDS);
            assertEquals(1,(x.isPresent()?1:0)+(y.isPresent()?1:0));
            var old=x.isPresent()?x.orElseThrow():y.orElseThrow();
            assertTrue((x.isPresent()?first:second).renew(old,60));
            jdbc.update("UPDATE mining_request SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE request_id=10");
            var replacement=(x.isPresent()?second:first).claim(1,10,"replacement",60).orElseThrow();
            assertTrue(replacement.fence()>old.fence());
            assertFalse((x.isPresent()?first:second).renew(old,60));
            assertThrows(IllegalStateException.class,()->(x.isPresent()?first:second).finish(old,()->(x.isPresent()?jdbc:jdbcB).update("INSERT INTO output_rows VALUES(10,'stale')")));
            (x.isPresent()?second:first).finish(replacement,()->(x.isPresent()?jdbcB:jdbc).update("INSERT INTO output_rows VALUES(10,'valid')"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM output_rows",Integer.class));
        } finally {pool.shutdownNow();}
    }
}
