package db.migration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts;
import vn.edu.fpt.paspm.utility.WeightV2Conversion;

/**
 * F03 Weight v2 (expiry risk + season). Additive schema plus a one-time rewrite of the LIVE Store
 * utility_config rows and the lambda policy row. Frozen JSON (utility_snapshot.config_snapshot,
 * mining_request.*, recommendation.*) is never read or written here.
 */
public final class V25__f03_weight_v2_expiry_season extends BaseJavaMigration {
    private static final String[] DDL={
        "ALTER TABLE inventory_batch ADD COLUMN push_day DATE NULL",
        "ALTER TABLE inventory_batch ADD CONSTRAINT ck_batch_push_day CHECK (push_day IS NULL OR expiry_date IS NULL OR push_day <= expiry_date)",
        """
        CREATE TABLE season (
            season_id INT AUTO_INCREMENT PRIMARY KEY,
            store_id INT NOT NULL,
            name VARCHAR(100) NOT NULL,
            start_date DATE NOT NULL,
            end_date DATE NOT NULL,
            weight DECIMAL(7,6) NOT NULL,
            version BIGINT NOT NULL DEFAULT 1,
            created_by INT NOT NULL,
            updated_by INT NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT uq_season_store_name UNIQUE (store_id, name),
            CONSTRAINT uq_season_store_id UNIQUE (store_id, season_id),
            CONSTRAINT ck_season_dates CHECK (start_date <= end_date),
            CONSTRAINT ck_season_weight CHECK (weight >= 0 AND weight <= 1),
            CONSTRAINT ck_season_version CHECK (version >= 1),
            CONSTRAINT fk_season_store FOREIGN KEY (store_id) REFERENCES store(store_id),
            CONSTRAINT fk_season_creator FOREIGN KEY (created_by) REFERENCES app_user(user_id),
            CONSTRAINT fk_season_updater FOREIGN KEY (updated_by) REFERENCES app_user(user_id)
        )""",
        """
        CREATE TABLE product_season (
            store_id INT NOT NULL,
            season_id INT NOT NULL,
            product_id INT NOT NULL,
            assigned_by INT NOT NULL,
            assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT pk_product_season PRIMARY KEY (season_id, product_id),
            CONSTRAINT fk_product_season_season FOREIGN KEY (store_id, season_id) REFERENCES season(store_id, season_id),
            CONSTRAINT fk_product_season_product FOREIGN KEY (store_id, product_id) REFERENCES product(store_id, product_id),
            CONSTRAINT fk_product_season_actor FOREIGN KEY (assigned_by) REFERENCES app_user(user_id)
        )""",
        "CREATE INDEX ix_product_season_product ON product_season(store_id, product_id)",
        """
        CREATE TABLE utility_snapshot_season (
            snapshot_id INT NOT NULL,
            store_id INT NOT NULL,
            season_id INT NOT NULL,
            name VARCHAR(100) NOT NULL,
            start_date DATE NOT NULL,
            end_date DATE NOT NULL,
            weight DECIMAL(7,6) NOT NULL,
            season_version BIGINT NOT NULL,
            CONSTRAINT pk_utility_snapshot_season PRIMARY KEY (snapshot_id, season_id),
            CONSTRAINT ck_snapshot_season_dates CHECK (start_date <= end_date),
            CONSTRAINT ck_snapshot_season_weight CHECK (weight >= 0 AND weight <= 1),
            CONSTRAINT fk_snapshot_season_snapshot FOREIGN KEY (store_id, snapshot_id) REFERENCES utility_snapshot(store_id, snapshot_id)
        )""",
        "ALTER TABLE utility_matrix_item ADD COLUMN expiry_risk DECIMAL(24,18) NULL",
        "ALTER TABLE utility_matrix_item ADD COLUMN season_weight DECIMAL(7,6) NULL",
        "ALTER TABLE utility_matrix_item ADD COLUMN season_id INT NULL",
        "ALTER TABLE utility_matrix_item ADD CONSTRAINT ck_utility_matrix_expiry_risk CHECK (expiry_risk IS NULL OR (expiry_risk >= 0 AND expiry_risk <= 1))",
        "ALTER TABLE utility_matrix_item ADD CONSTRAINT ck_utility_matrix_season_weight CHECK (season_weight IS NULL OR (season_weight >= 0 AND season_weight <= 1))",
        "ALTER TABLE utility_matrix_item ADD CONSTRAINT fk_utility_matrix_snapshot_season FOREIGN KEY (snapshot_id, season_id) REFERENCES utility_snapshot_season(snapshot_id, season_id)",
        """
        CREATE TABLE utility_weight_v2_conversion (
            conversion_id INT AUTO_INCREMENT PRIMARY KEY,
            subject VARCHAR(10) NOT NULL,
            store_id INT NULL,
            rule_version VARCHAR(40) NOT NULL,
            had_config BOOLEAN NOT NULL,
            old_value JSON NULL,
            new_value JSON NOT NULL,
            old_stock_coefficient DECIMAL(30,6) NULL,
            old_expiry_coefficient DECIMAL(30,6) NULL,
            old_season_coefficient DECIMAL(30,6) NULL,
            new_alpha_expiry DECIMAL(7,6) NULL,
            new_beta_season DECIMAL(7,6) NULL,
            default_coefficients BOOLEAN NOT NULL,
            old_lambda DECIMAL(30,6) NULL,
            new_lambda DECIMAL(30,6) NULL,
            lambda_max DECIMAL(30,6) NOT NULL,
            lambda_clamped BOOLEAN NOT NULL,
            converted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT ck_weight_v2_conversion_subject CHECK (subject IN ('STORE','POLICY')),
            CONSTRAINT uq_weight_v2_conversion_store UNIQUE (subject, store_id),
            CONSTRAINT fk_weight_v2_conversion_store FOREIGN KEY (store_id) REFERENCES store(store_id)
        )"""
    };

    @Override public void migrate(Context context) throws Exception {
        Connection c=context.getConnection();
        try(var st=c.createStatement()) { for(String sql:DDL)st.execute(sql); }
        replaceExpiryStatusCheck(c);
        BigDecimal lambdaMax=convertPolicy(c);
        convertStores(c,lambdaMax);
    }

    /**
     * The V5 name ck_utility_matrix_expiry_status is not guaranteed: a database restored through the V11 legacy
     * recovery path carries a recreated table. Drop every CHECK on utility_matrix_item that covers expiry_status
     * (found by catalog lookup), then add the extended rule as ck_utility_matrix_expiry_status_w2 (CHECK names are schema-wide in MySQL and the recovered archive may still own the V5 name).
     */
    private void replaceExpiryStatusCheck(Connection c) throws SQLException {
        boolean mysql=c.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
        String sql=mysql
            ?"SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='utility_matrix_item' AND tc.CONSTRAINT_TYPE='CHECK' AND LOWER(cc.CHECK_CLAUSE) LIKE '%expiry_status%'"
            :"SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=SCHEMA() AND LOWER(tc.TABLE_NAME)='utility_matrix_item' AND tc.CONSTRAINT_TYPE='CHECK' AND LOWER(cc.CHECK_CLAUSE) LIKE '%expiry_status%'";
        var names=new java.util.ArrayList<String>();
        try(var st=c.createStatement();var r=st.executeQuery(sql)) { while(r.next())names.add(r.getString(1)); }
        try(var st=c.createStatement()) {
            for(String name:names) {
                if(!name.matches("[A-Za-z0-9_]+"))throw new SQLException("Unexpected constraint name "+name);
                st.execute("ALTER TABLE utility_matrix_item DROP CONSTRAINT "+(mysql?"`"+name+"`":"\""+name+"\""));
            }
            st.execute("ALTER TABLE utility_matrix_item ADD CONSTRAINT ck_utility_matrix_expiry_status_w2 CHECK (expiry_status IN ('AVAILABLE','NOT_APPLICABLE','NO_SELLABLE_BATCH','MISSING_METADATA','MISSING_PUSH_DAY','SLOW_MOVING'))");
        }
    }

    private BigDecimal convertPolicy(Connection c) throws SQLException {
        String raw;
        try(var ps=c.prepareStatement("SELECT config_value FROM system_configuration WHERE config_key='UTILITY_LAMBDA_POLICY' FOR UPDATE");
            var r=ps.executeQuery()) {
            if(!r.next())throw new SQLException("UTILITY_LAMBDA_POLICY row is missing");
            raw=r.getString(1);
        }
        BigDecimal max,min;
        try {
            var node=WeightV2Conversion.unwrap(new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(raw));
            if(!node.path("lambdaMax").isNumber())throw new SQLException("UTILITY_LAMBDA_POLICY.lambdaMax is not numeric");
            max=node.path("lambdaMax").decimalValue();
            min=node.path("lambdaMin").isNumber()?node.path("lambdaMin").decimalValue():null;
        } catch(java.io.IOException e) { throw new SQLException("UTILITY_LAMBDA_POLICY is not valid JSON",e); }
        if(max.signum()<0)throw new SQLException("UTILITY_LAMBDA_POLICY.lambdaMax is negative");
        String plainMax=max.stripTrailingZeros().scale()<0?max.stripTrailingZeros().setScale(0).toPlainString():max.stripTrailingZeros().toPlainString();
        String next="{\"scoringVersion\":\""+UtilityVersionedContracts.V2+"\",\"lambdaMax\":"+plainMax+"}";
        try(var ps=c.prepareStatement("UPDATE system_configuration SET config_value=?,config_version=config_version+1,updated_at=CURRENT_TIMESTAMP WHERE config_key='UTILITY_LAMBDA_POLICY'")) {
            ps.setString(1,next);ps.executeUpdate();
        }
        try(var ps=c.prepareStatement("INSERT INTO utility_weight_v2_conversion(subject,store_id,rule_version,had_config,old_value,new_value,default_coefficients,old_lambda,new_lambda,lambda_max,lambda_clamped) VALUES ('POLICY',NULL,?,TRUE,?,?,FALSE,?,?,?,FALSE)")) {
            ps.setString(1,WeightV2Conversion.RULE);ps.setString(2,raw);ps.setString(3,next);
            // For the policy row old_lambda/new_lambda carry the lower bound before (old lambdaMin) and after (fixed 0).
            ps.setBigDecimal(4,min);ps.setBigDecimal(5,BigDecimal.ZERO);ps.setBigDecimal(6,max);
            ps.executeUpdate();
        }
        return max;
    }

    private void convertStores(Connection c,BigDecimal lambdaMax) throws SQLException {
        var rows=new java.util.ArrayList<Object[]>();
        try(var ps=c.prepareStatement("SELECT store_id,utility_config FROM store ORDER BY store_id FOR UPDATE");var r=ps.executeQuery()) {
            while(r.next())rows.add(new Object[]{r.getInt(1),r.getString(2)});
        }
        try(var update=c.prepareStatement("UPDATE store SET utility_config=? WHERE store_id=?");
            var evidence=c.prepareStatement("INSERT INTO utility_weight_v2_conversion(subject,store_id,rule_version,had_config,old_value,new_value,old_stock_coefficient,old_expiry_coefficient,old_season_coefficient,new_alpha_expiry,new_beta_season,default_coefficients,old_lambda,new_lambda,lambda_max,lambda_clamped) VALUES ('STORE',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for(var row:rows) {
                int store=(Integer)row[0];String raw=(String)row[1];
                WeightV2Conversion.Result result;
                try { result=WeightV2Conversion.convert(raw,lambdaMax); }
                catch(IllegalArgumentException e) { throw new SQLException("Store "+store+": "+e.getMessage(),e); }
                update.setString(1,result.json());update.setInt(2,store);update.executeUpdate();
                evidence.setInt(1,store);evidence.setString(2,WeightV2Conversion.RULE);evidence.setBoolean(3,result.hadConfig());
                if(raw==null)evidence.setNull(4,Types.VARCHAR);else evidence.setString(4,raw);
                evidence.setString(5,result.json());
                evidence.setBigDecimal(6,result.oldStockCoefficient());evidence.setBigDecimal(7,result.oldExpiryCoefficient());
                evidence.setBigDecimal(8,result.oldSeasonCoefficient());evidence.setBigDecimal(9,result.alpha());
                evidence.setBigDecimal(10,result.beta());evidence.setBoolean(11,result.defaultCoefficients());
                evidence.setBigDecimal(12,result.oldLambda());evidence.setBigDecimal(13,result.lambda());
                evidence.setBigDecimal(14,lambdaMax);evidence.setBoolean(15,result.lambdaClamped());
                evidence.executeUpdate();
            }
        }
    }
}
