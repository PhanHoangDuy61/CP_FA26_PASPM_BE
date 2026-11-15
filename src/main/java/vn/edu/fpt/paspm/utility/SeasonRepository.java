package vn.edu.fpt.paspm.utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.utility.SeasonContracts.*;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.SeasonCopy;

@Repository
public class SeasonRepository {
    private final JdbcTemplate jdbc;
    public SeasonRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String VIEW="""
        SELECT s.*,(SELECT COUNT(*) FROM product_season ps WHERE ps.season_id=s.season_id) AS product_count
        FROM season s""";
    private SeasonView view(ResultSet r,LocalDate today) throws SQLException {
        var start=r.getObject("start_date",LocalDate.class);var end=r.getObject("end_date",LocalDate.class);
        return new SeasonView(r.getInt("season_id"),r.getInt("store_id"),r.getString("name"),start,end,r.getBigDecimal("weight"),
            r.getLong("version"),r.getInt("product_count"),!today.isBefore(start)&&!today.isAfter(end),r.getInt("created_by"),
            r.getInt("updated_by"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    }
    public List<SeasonView> list(int store,int page,int size,LocalDate today) {
        return jdbc.query(VIEW+" WHERE s.store_id=? ORDER BY s.start_date,s.season_id LIMIT ? OFFSET ?",(r,n)->view(r,today),store,size,(long)page*size);
    }
    public long count(int store) { return jdbc.queryForObject("SELECT COUNT(*) FROM season WHERE store_id=?",Long.class,store); }
    public Optional<SeasonView> find(int store,int id,LocalDate today,boolean lock) {
        if(lock)jdbc.query("SELECT season_id FROM season WHERE store_id=? AND season_id=? FOR UPDATE",(r,n)->r.getInt(1),store,id);
        return jdbc.query(VIEW+" WHERE s.store_id=? AND s.season_id=?",(r,n)->view(r,today),store,id).stream().findFirst();
    }
    public boolean nameTaken(int store,String name,Integer except) {
        String sql="SELECT COUNT(*) FROM season WHERE store_id=? AND LOWER(name)=LOWER(?)";
        return (except==null?jdbc.queryForObject(sql,Long.class,store,name.strip())
            :jdbc.queryForObject(sql+" AND season_id<>?",Long.class,store,name.strip(),except))>0;
    }
    public int create(int store,int actor,SeasonRequest r) {
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO season(store_id,name,start_date,end_date,weight,created_by,updated_by) VALUES (?,?,?,?,?,?,?)",new String[]{"season_id"});
            ps.setInt(1,store);ps.setString(2,r.name().strip());ps.setObject(3,r.startDate());ps.setObject(4,r.endDate());ps.setBigDecimal(5,r.weight());ps.setInt(6,actor);ps.setInt(7,actor);return ps;},key);
        return key.getKey().intValue();
    }
    public boolean update(int store,int id,int actor,SeasonRequest r) {
        return jdbc.update("UPDATE season SET name=?,start_date=?,end_date=?,weight=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE store_id=? AND season_id=? AND version=?",
            r.name().strip(),r.startDate(),r.endDate(),r.weight(),actor,store,id,r.expectedVersion())==1;
    }
    public void delete(int store,int id) { jdbc.update("DELETE FROM season WHERE store_id=? AND season_id=?",store,id); }
    public boolean productExists(int store,int product) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE store_id=? AND product_id=?",Long.class,store,product)>0;
    }
    public boolean assign(int store,int season,int product,int actor) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM product_season WHERE season_id=? AND product_id=?",Long.class,season,product)>0)return false;
        jdbc.update("INSERT INTO product_season(store_id,season_id,product_id,assigned_by) VALUES (?,?,?,?)",store,season,product,actor);return true;
    }
    public boolean unassign(int store,int season,int product) {
        return jdbc.update("DELETE FROM product_season WHERE store_id=? AND season_id=? AND product_id=?",store,season,product)==1;
    }
    public List<SeasonProduct> products(int store,int season,int page,int size) {
        return jdbc.query("SELECT p.product_id,p.sku,p.name,p.status,ps.assigned_by,ps.assigned_at FROM product_season ps JOIN product p ON p.store_id=ps.store_id AND p.product_id=ps.product_id WHERE ps.store_id=? AND ps.season_id=? ORDER BY p.product_id LIMIT ? OFFSET ?",
            (r,n)->new SeasonProduct(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getTimestamp(6).toInstant()),store,season,size,(long)page*size);
    }
    public long productCount(int store,int season) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM product_season WHERE store_id=? AND season_id=?",Long.class,store,season);
    }
    /** P3: active when start_date <= analysisDate <= end_date (inclusive). Copied by value into snapshots. */
    public List<SeasonCopy> active(int store,LocalDate analysisDate) {
        var seasons=jdbc.query("SELECT season_id,name,start_date,end_date,weight,version FROM season WHERE store_id=? AND start_date<=? AND end_date>=? ORDER BY season_id",
            (r,n)->new Object[]{r.getInt(1),r.getString(2),r.getObject(3,LocalDate.class),r.getObject(4,LocalDate.class),r.getBigDecimal(5),r.getLong(6)},store,analysisDate,analysisDate);
        var products=new HashMap<Integer,List<Integer>>();
        jdbc.query("SELECT ps.season_id,ps.product_id FROM product_season ps JOIN season s ON s.season_id=ps.season_id AND s.store_id=ps.store_id WHERE ps.store_id=? AND s.start_date<=? AND s.end_date>=? ORDER BY ps.season_id,ps.product_id",
            (org.springframework.jdbc.core.RowCallbackHandler)r->products.computeIfAbsent(r.getInt(1),k->new ArrayList<>()).add(r.getInt(2)),store,analysisDate,analysisDate);
        return seasons.stream().map(s->new SeasonCopy((Integer)s[0],(String)s[1],(LocalDate)s[2],(LocalDate)s[3],
            ((java.math.BigDecimal)s[4]).stripTrailingZeros().scale()<0?((java.math.BigDecimal)s[4]).setScale(0):((java.math.BigDecimal)s[4]).stripTrailingZeros(),
            (Long)s[5],List.copyOf(products.getOrDefault((Integer)s[0],List.of())))).toList();
    }
}
