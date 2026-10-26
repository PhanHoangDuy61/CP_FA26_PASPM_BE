package vn.edu.fpt.paspm.recommendation;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

/** Opt-in: MySqlTestDatabase allocates a new schema on the explicitly supplied disposable server. */
@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
class RecommendationHardeningMySqlIntegrationTest extends RecommendationHardeningIntegrationTest {
    @org.junit.jupiter.api.Test void explainMeasuredReadQueriesOnDisposableMySql() throws Exception {
        var ids=seed(100,RecommendationContracts.RecType.BANNER);
        postJson("/api/recommendations/"+ids.get(0)+"/reject",merchant,"{\"expectedVersion\":1,\"reason\":\"Representative history row for EXPLAIN\"}").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        String marks=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
        var arguments=new java.util.ArrayList<Object>();arguments.add(store);arguments.addAll(ids);
        var plans=new java.util.LinkedHashMap<String,Object>();
        plans.put("base",jdbc.queryForList("EXPLAIN ANALYZE SELECT r.* FROM recommendation r WHERE r.store_id=? ORDER BY r.recommendation_id DESC LIMIT 100 OFFSET 0",store));
        plans.put("items",jdbc.queryForList("EXPLAIN ANALYZE SELECT recommendation_id,product_id FROM recommendation_item WHERE store_id=? AND recommendation_id IN ("+marks+") ORDER BY recommendation_id,product_id",arguments.toArray()));
        plans.put("sources",jdbc.queryForList("EXPLAIN ANALYZE SELECT recommendation_id,provenance FROM recommendation_source WHERE store_id=? AND recommendation_id IN ("+marks+") ORDER BY recommendation_id,run_id,pattern_id",arguments.toArray()));
        plans.put("history",jdbc.queryForList("EXPLAIN ANALYZE SELECT * FROM recommendation_review_history WHERE store_id=? AND recommendation_id IN ("+marks+") ORDER BY recommendation_id,resulting_version",arguments.toArray()));
        org.junit.jupiter.api.Assertions.assertEquals(4,plans.size());
        System.out.println("F06_EXPLAIN "+json.writeValueAsString(plans));
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){MySqlTestDatabase.create().register(registry);}
}
