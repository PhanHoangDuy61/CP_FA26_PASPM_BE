package vn.edu.fpt.paspm.utility;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

@Repository
public class UtilitySettingsRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public UtilitySettingsRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    public LambdaPolicy policy(boolean lock) {
        return jdbc.queryForObject("SELECT config_value,config_version FROM system_configuration WHERE config_key='UTILITY_LAMBDA_POLICY'"+(lock?" FOR UPDATE":""),
            (rs,row)-> { var p=read(rs.getString(1),PolicyRequest.class); return new LambdaPolicy(p.lambdaMin(),p.lambdaMax(),rs.getLong(2)); });
    }
    public void policy(PolicyRequest request,int actor) {
        jdbc.update("UPDATE system_configuration SET config_value=?,config_version=config_version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE config_key='UTILITY_LAMBDA_POLICY'",write(request),actor);
    }
    public WeightConfig config(int storeId,boolean lock) {
        String raw=jdbc.queryForObject("SELECT utility_config FROM store WHERE store_id=?"+(lock?" FOR UPDATE":""),String.class,storeId);
        return raw==null?WeightConfig.defaults():read(raw,WeightConfig.class);
    }
    public void config(int storeId,WeightConfig config) {
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
