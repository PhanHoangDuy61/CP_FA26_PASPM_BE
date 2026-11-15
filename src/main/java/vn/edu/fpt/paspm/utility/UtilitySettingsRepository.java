package vn.edu.fpt.paspm.utility;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.*;

@Repository
public class UtilitySettingsRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public UtilitySettingsRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    /** Current (W2_EXPIRY_SEASON_V1) policy. A legacy row means V25 has not run; never guess. */
    public LambdaV2 policy(boolean lock) {
        var policy=versionedPolicy(lock);
        if(policy instanceof LambdaV2 current)return current;
        throw new IllegalStateException("Legacy UTILITY_LAMBDA_POLICY row; migration V25 has not converted it");
    }
    public PolicyValues versionedPolicy(boolean lock) {
        return jdbc.queryForObject("SELECT config_value,config_version FROM system_configuration WHERE config_key='UTILITY_LAMBDA_POLICY'"+(lock?" FOR UPDATE":""),
            (rs,row)-> { var p=read(rs.getString(1),PolicyPayload.class);
                return p instanceof PolicyPayloadV2 current?new LambdaV2(current.scoringVersion(),current.lambdaMax(),rs.getLong(2)):
                    new LambdaPolicy(p.lambdaMin(),p.lambdaMax(),rs.getLong(2)); });
    }
    public void policy(PolicyPayloadV2 payload,int actor) {
        jdbc.update("UPDATE system_configuration SET config_value=?,config_version=config_version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE config_key='UTILITY_LAMBDA_POLICY'",write(payload),actor);
    }
    /** Current (W2_EXPIRY_SEASON_V1) Store config; a Store without one uses {@link WeightV2#defaults()}. */
    public WeightV2 config(int storeId,boolean lock) {
        var parsed=versionedConfig(storeId,lock);
        if(parsed instanceof WeightV2 current)return current;
        throw new IllegalStateException("Legacy Store utility_config; migration V25 has not converted Store "+storeId);
    }
    /** Read-only version dispatch. */
    public WeightSettings versionedConfig(int storeId,boolean lock) {
        String raw=jdbc.queryForObject("SELECT utility_config FROM store WHERE store_id=?"+(lock?" FOR UPDATE":""),String.class,storeId);
        return raw==null?WeightV2.defaults():read(raw,WeightSettings.class);
    }
    public void config(int storeId,WeightV2 config) {
        jdbc.update("UPDATE store SET utility_config=? WHERE store_id=?",write(config),storeId);
    }
    public String write(Object value) {
        try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("Cannot encode utility settings",e); }
    }
    public <T> T read(String raw,Class<T> type) {
        try {
            var node=json.readTree(raw);
            if(node.isTextual()) node=json.readTree(node.asText()); // H2 JSON stores bound strings as JSON strings.
            return json.treeToValue(node,type);
        } catch(Exception e) { throw new IllegalStateException("Invalid persisted utility settings",e); }
    }
}
