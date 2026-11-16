package db.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * DEC-20261116-02 (P5 changed): drop the legacy product scoring columns that W2_EXPIRY_SEASON_V1 no longer reads:
 * {@code expiry_push_days} (V8) and {@code stock_priority_score}, {@code seasonal_priority_score} (V4).
 * Frozen JSON (snapshots, matrix evidence, mining requests, recommendations) is untouched; it never referenced these
 * columns. MySQL and H2 both refuse to drop a column that a CHECK constraint references, and constraint names are not
 * assumed (lesson of V25), so every CHECK on {@code product} covering one of the columns is found through the catalog
 * and dropped first. Not reversible without the pre-migration backup.
 */
public final class V26__f03_drop_legacy_product_scoring_columns extends BaseJavaMigration {
    static final List<String> COLUMNS=List.of("expiry_push_days","stock_priority_score","seasonal_priority_score");

    @Override public void migrate(Context context) throws Exception {
        Connection c=context.getConnection();
        boolean mysql=c.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
        try(var st=c.createStatement()) {
            for(String name:checksCovering(c,mysql)) {
                if(!name.matches("[A-Za-z0-9_]+"))throw new SQLException("Unexpected constraint name "+name);
                st.execute("ALTER TABLE product DROP CONSTRAINT "+(mysql?"`"+name+"`":"\""+name+"\""));
            }
            for(String column:COLUMNS)st.execute("ALTER TABLE product DROP COLUMN "+column);
        }
    }

    private static List<String> checksCovering(Connection c,boolean mysql) throws SQLException {
        String sql=mysql
            ?"SELECT tc.CONSTRAINT_NAME,cc.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='product' AND tc.CONSTRAINT_TYPE='CHECK'"
            :"SELECT tc.CONSTRAINT_NAME,cc.CHECK_CLAUSE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=SCHEMA() AND LOWER(tc.TABLE_NAME)='product' AND tc.CONSTRAINT_TYPE='CHECK'";
        var names=new ArrayList<String>();
        try(var st=c.createStatement();var r=st.executeQuery(sql)) {
            while(r.next()) {
                String clause=r.getString(2)==null?"":r.getString(2).toLowerCase(Locale.ROOT);
                if(COLUMNS.stream().anyMatch(clause::contains))names.add(r.getString(1));
            }
        }
        return names;
    }
}
