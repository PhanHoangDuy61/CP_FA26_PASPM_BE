package vn.edu.fpt.paspm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class SessionV21MigrationTest {
    @Test void publishedV20RowsAndChecksumArePreserved() throws Exception {
        verify("jdbc:h2:mem:session_upgrade_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }
    static void verify(String url, String user, String password) throws Exception {
        var old=Flyway.configure().dataSource(url,user,password).target("20").load();old.migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        String email="legacy-"+UUID.randomUUID()+"@example.invalid",sid=UUID.randomUUID().toString();
        db.update("INSERT INTO app_user(email,role_id,status) SELECT ?,role_id,'ACTIVE' FROM role WHERE code='END_CUSTOMER'",email);
        int id=db.queryForObject("SELECT user_id FROM app_user WHERE email=?",Integer.class,email);
        Timestamp now=Timestamp.from(Instant.now()),expires=Timestamp.from(Instant.now().plusSeconds(3600));
        db.update("INSERT INTO auth_refresh_session(session_id,user_id,token_version,created_at,last_used_at,absolute_expires_at,idle_expires_at) VALUES(?,?,0,?,?,?,?)",sid,id,now,now,expires,expires);
        byte[] hash=MessageDigest.getInstance("SHA-256").digest("synthetic-legacy-token".getBytes(StandardCharsets.UTF_8));
        db.update("INSERT INTO auth_refresh_token(session_id,token_hash,created_at) VALUES(?,?,?)",sid,hash,now);
        var before=db.queryForMap("SELECT * FROM auth_refresh_session WHERE session_id=?",sid);
        int checksum=db.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='20'",Integer.class);
        var latest=Flyway.configure().dataSource(url,user,password).load();assertEquals(1,latest.migrate().migrationsExecuted);latest.validate();
        assertEquals(before,db.queryForMap("SELECT * FROM auth_refresh_session WHERE session_id=?",sid));
        assertArrayEquals(hash,db.queryForObject("SELECT token_hash FROM auth_refresh_token WHERE session_id=?",byte[].class,sid));
        assertNull(db.queryForObject("SELECT csrf_hash FROM auth_refresh_token WHERE session_id=?",byte[].class,sid));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM auth_refresh_token",Integer.class));
        assertEquals(checksum,db.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='20'",Integer.class));
        assertEquals(0,latest.migrate().migrationsExecuted);
    }
}
