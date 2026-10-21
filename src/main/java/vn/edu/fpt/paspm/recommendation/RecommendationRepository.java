package vn.edu.fpt.paspm.recommendation;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilitySettingsRepository;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

@Repository
public class RecommendationRepository {
    private final JdbcTemplate jdbc;private final UtilitySettingsRepository json;
    public RecommendationRepository(JdbcTemplate jdbc,UtilitySettingsRepository json){this.jdbc=jdbc;this.json=json;}
    public record Stored(long id,int store,int snapshot,String key,RecType type,List<Integer> products,Integer anchor,
        java.math.BigDecimal utility,ReviewStatus status,long version,RecPolicy policy,TransactionEvidence.Evidence evidence,
        RecEligibility eligibility,List<String> explanation,Integer reviewer,Instant reviewedAt,String reason,Instant createdAt) {}
    public Stored find(int store,long id,boolean lock) {
        return jdbc.query("SELECT * FROM recommendation WHERE store_id=? AND recommendation_id=?"+(lock?" FOR UPDATE":""),this::row,store,id)
            .stream().findFirst().orElseThrow(()->new ApiException(404,"Recommendation not found"));
    }
    public Long byIdentity(int store,String identity){return jdbc.queryForList("SELECT recommendation_id FROM recommendation WHERE store_id=? AND identity_hash=?",Long.class,store,identity).stream().findFirst().orElse(null);}
    private Stored row(ResultSet r,int n)throws SQLException {
        long id=r.getLong("recommendation_id");int store=r.getInt("store_id");var reviewed=r.getTimestamp("reviewed_at");
        var products=jdbc.queryForList("SELECT product_id FROM recommendation_item WHERE store_id=? AND recommendation_id=? ORDER BY product_id",Integer.class,store,id);
        return new Stored(id,store,r.getInt("utility_snapshot_id"),r.getString("canonical_key"),RecType.valueOf(r.getString("type")),products,r.getObject("anchor_product_id",Integer.class),r.getBigDecimal("utility"),ReviewStatus.valueOf(r.getString("review_status")),r.getLong("version"),json.read(r.getString("policy_snapshot"),RecPolicy.class),json.read(r.getString("evidence"),TransactionEvidence.Evidence.class),json.read(r.getString("generation_eligibility"),RecEligibility.class),List.of(json.read(r.getString("explanation"),String[].class)),r.getObject("reviewed_by",Integer.class),reviewed==null?null:reviewed.toInstant(),r.getString("review_reason"),r.getTimestamp("created_at").toInstant());
    }
    public long create(int store,int actor,int snapshot,String identity,String key,RecommendationRules.Candidate candidate,RecPolicy policy,RecEligibility eligibility) {
        long id=insert("INSERT INTO recommendation(store_id,utility_snapshot_id,identity_hash,canonical_key,type,anchor_product_id,utility,support_count,rule_version,policy_version,policy_snapshot,evidence,generation_eligibility,explanation,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)","recommendation_id",store,snapshot,identity,key,candidate.type().name(),candidate.anchor(),candidate.utility(),candidate.evidence().supportCount(),policy.ruleVersion(),policy.version(),json.write(policy),json.write(candidate.evidence()),json.write(eligibility),json.write(candidate.explanation()),actor);
        for(int product:candidate.productIds())jdbc.update("INSERT INTO recommendation_item(recommendation_id,store_id,product_id) VALUES (?,?,?)",id,store,product);
        return id;
    }
    public void source(int store,long id,RecSource source) {
        int existing=jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_source WHERE recommendation_id=? AND run_id=? AND pattern_id=?",Integer.class,id,source.runId(),source.patternId());
        if(existing==0)jdbc.update("INSERT INTO recommendation_source(store_id,recommendation_id,run_id,pattern_id,provenance) VALUES (?,?,?,?,?)",store,id,source.runId(),source.patternId(),json.write(source));
    }
    public List<RecSource> sources(int store,long id){return jdbc.query("SELECT provenance FROM recommendation_source WHERE store_id=? AND recommendation_id=? ORDER BY run_id,pattern_id",(r,n)->json.read(r.getString(1),RecSource.class),store,id);}
    public List<RecReview> history(int store,long id){return jdbc.query("SELECT * FROM recommendation_review_history WHERE store_id=? AND recommendation_id=? ORDER BY resulting_version",(r,n)->new RecReview(ReviewStatus.valueOf(r.getString("old_status")),ReviewStatus.valueOf(r.getString("new_status")),r.getInt("reviewed_by"),r.getTimestamp("reviewed_at").toInstant(),r.getString("reason"),r.getLong("resulting_version"),json.read(r.getString("eligibility_check"),RecEligibility.class)),store,id);}
    private record Filter(String sql,List<Object> args) {}
    private Filter filter(int store,Long run,Integer snapshot,RecType type,ReviewStatus status) {
        StringBuilder where=new StringBuilder(" WHERE r.store_id=?");var args=new ArrayList<Object>();args.add(store);
        if(run!=null){where.append(" AND EXISTS(SELECT 1 FROM recommendation_source s WHERE s.recommendation_id=r.recommendation_id AND s.store_id=r.store_id AND s.run_id=?)");args.add(run);}
        if(snapshot!=null){where.append(" AND r.utility_snapshot_id=?");args.add(snapshot);}
        if(type!=null){where.append(" AND r.type=?");args.add(type.name());}
        if(status!=null){where.append(" AND r.review_status=?");args.add(status.name());}
        return new Filter(where.toString(),args);
    }
    public long count(int store,Long run,Integer snapshot,RecType type,ReviewStatus status) {
        var f=filter(store,run,snapshot,type,status);
        return jdbc.queryForObject("SELECT COUNT(*) FROM recommendation r"+f.sql(),Long.class,f.args().toArray());
    }
    public List<Stored> list(int store,Long run,Integer snapshot,RecType type,ReviewStatus status,int limit,long offset) {
        var f=filter(store,run,snapshot,type,status);f.args().add(limit);f.args().add(offset);
        return jdbc.query("SELECT r.* FROM recommendation r"+f.sql()+" ORDER BY r.recommendation_id DESC LIMIT ? OFFSET ?",this::row,f.args().toArray());
    }
    public void review(Stored rec,int actor,RecReviewRequest request,ReviewStatus next,RecEligibility eligibility) {
        int changed=jdbc.update("UPDATE recommendation SET review_status=?,version=version+1,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_reason=? WHERE store_id=? AND recommendation_id=? AND version=? AND review_status='PENDING_REVIEW'",next.name(),actor,request.reason().trim(),rec.store(),rec.id(),request.expectedVersion());
        if(changed!=1)throw new ApiException(409,"Recommendation review version or state changed");
        jdbc.update("INSERT INTO recommendation_review_history(store_id,recommendation_id,old_status,new_status,reviewed_by,reason,resulting_version,eligibility_check) VALUES (?,?,'PENDING_REVIEW',?,?,?,?,?)",rec.store(),rec.id(),next.name(),actor,request.reason().trim(),rec.version()+1,json.write(eligibility));
    }
    private long insert(String sql,String key,Object...args){var holder=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement(sql,new String[]{key});for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},holder);return Objects.requireNonNull(holder.getKey()).longValue();}
}
