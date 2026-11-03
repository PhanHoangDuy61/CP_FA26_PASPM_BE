package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/** Explicit QA-only source. Never active in normal dev or production startup. */
@Component
@Profile("dev & utility-fixture")
@Order(10)
public class UtilityFixtureSource implements TransactionSource,ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AuthService auth;
    private final LocalDate analysisDate;
    private final String password;
    public UtilityFixtureSource(JdbcTemplate jdbc,UserRepository users,AuthService auth,
        @Value("${paspm.utility.fixture-analysis-date}") LocalDate analysisDate,
        @Value("${paspm.utility.fixture-password}") String password) {
        if(password.length()<12)throw new IllegalArgumentException("Fixture password must be supplied explicitly and contain at least 12 characters");
        this.jdbc=jdbc;this.users=users;this.auth=auth;this.analysisDate=analysisDate;this.password=password;
    }
    @Override @Transactional public void run(ApplicationArguments arguments) {
        int a=store("F03 Fixture Store A"),b=store("F03 Fixture Store B");
        user("f03-system@fixture.paspm.test","SYSTEM_ADMIN",null);
        user("f03-admin-a@fixture.paspm.test","STORE_ADMIN",a);user("f03-admin-b@fixture.paspm.test","STORE_ADMIN",b);
        user("f03-merchant-a@fixture.paspm.test","MERCHANDISER",a);user("f03-merchant-b@fixture.paspm.test","MERCHANDISER",b);
        for(int store:new int[]{a,b}) {
            int milk=product(store,"F03-FIX-MILK",true),coffee=product(store,"F03-FIX-COFFEE",false);
            batch(milk,"FIVE",analysisDate.plusDays(5),20,"ACTIVE");
            batch(milk,"FORTY",analysisDate.plusDays(40),80,"ACTIVE");
            batch(milk,"EXPIRED",analysisDate.minusDays(1),4,"ACTIVE");
            batch(milk,"BLOCKED",analysisDate.plusDays(2),5,"BLOCKED");
            batch(coffee,"NO-EXPIRY",null,9,"ACTIVE");
            int missing=product(store,"F03-FIX-MISSING",true);
            batch(missing,"MISSING-EXPIRY",null,5,"ACTIVE");
        }
    }
    private int store(String name) {
        var ids=jdbc.query("SELECT store_id FROM store WHERE name=? ORDER BY store_id",(rs,row)->rs.getInt(1),name);
        if(!ids.isEmpty())return ids.get(0);
        jdbc.update("INSERT INTO store(name) VALUES (?)",name);
        return jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
    }
    private void user(String email,String role,Integer store) {
        if(users.findByEmail(email).isPresent())return;
        var user=users.createPendingUser("F03 QA "+role,email,role,store,auth.encodePassword(password),null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?",Timestamp.from(Instant.now()),user.id());
    }
    private int product(int store,String sku,boolean expiry) {
        var ids=jdbc.query("SELECT product_id FROM product WHERE store_id=? AND sku=?",(rs,row)->rs.getInt(1),store,sku);
        if(!ids.isEmpty())return ids.get(0);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status,stock_priority_score,seasonal_priority_score) VALUES (?,?,?,'each',?,100,20,'ACTIVE',0.8,0.9)",store,sku,"F03 Fixture "+sku,expiry);
        return jdbc.queryForObject("SELECT product_id FROM product WHERE store_id=? AND sku=?",Integer.class,store,sku);
    }
    private void batch(int product,String code,LocalDate expiry,int quantity,String status) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch WHERE product_id=? AND batch_code=?",Integer.class,product,code)>0)return;
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status) VALUES (?,?,?,?,?,?,?)",product,code,Timestamp.from(Instant.now()),expiry,quantity,quantity,status);
    }
    @Override public String sourceType() { return "DEV_FIXTURE"; }
    @Override public List<PreparedTransaction> load(int store,LocalDate start,LocalDate end) {
        var ids=jdbc.query("SELECT product_id,sku FROM product WHERE store_id=? AND sku IN ('F03-FIX-MILK','F03-FIX-COFFEE','F03-FIX-MISSING')",(rs,row)->new Object[]{rs.getInt(1),rs.getString(2)},store);
        Integer milk=null,coffee=null,missing=null;
        for(var entry:ids) { if("F03-FIX-MILK".equals(entry[1]))milk=(Integer)entry[0];else if("F03-FIX-COFFEE".equals(entry[1]))coffee=(Integer)entry[0];else missing=(Integer)entry[0]; }
        if(milk==null||coffee==null||missing==null)return List.of();
        return List.of(
            new PreparedTransaction(store,1,analysisDate.minusDays(2),true,List.of(new Item(milk,3,new BigDecimal("10000"),new BigDecimal("6000"),null))),
            new PreparedTransaction(store,2,analysisDate.minusDays(1),true,List.of(new Item(coffee,2,new BigDecimal("25000"),new BigDecimal("10000"),null))),
            new PreparedTransaction(store,3,analysisDate,true,List.of(new Item(milk,1,new BigDecimal("9000"),new BigDecimal("6000"),null),new Item(coffee,1,new BigDecimal("20000"),new BigDecimal("10000"),null))),
            new PreparedTransaction(store,4,analysisDate.minusYears(1),true,List.of(new Item(missing,1,new BigDecimal("10000"),new BigDecimal("6000"),null))),
            new PreparedTransaction(store,5,analysisDate.minusYears(2),true,List.of(new Item(milk,1,BigDecimal.ONE,BigDecimal.TEN,null))),
            new PreparedTransaction(store,6,analysisDate.minusYears(3),true,List.of(new Item(milk,1,BigDecimal.TEN,BigDecimal.ONE,null),new Item(milk,2,BigDecimal.TEN,BigDecimal.ONE,null)))
        ).stream().filter(t->!t.occurredOn().isBefore(start)&&!t.occurredOn().isAfter(end)).toList();
    }
}
