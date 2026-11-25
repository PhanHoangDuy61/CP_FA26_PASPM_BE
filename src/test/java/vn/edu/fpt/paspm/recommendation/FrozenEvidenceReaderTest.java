package vn.edu.fpt.paspm.recommendation;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.*;
import vn.edu.fpt.paspm.utility.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrozenEvidenceReaderTest {
    JdbcTemplate jdbc;MiningRepository mining;UtilitySnapshotRepository snapshots;UtilitySettingsRepository json;
    FrozenEvidenceReader reader;MiningInputBuilder.Dataset input;MiningContracts.RunView run;
    UtilityMatrixContracts.SnapshotView snapshot;
    @BeforeEach void fixture() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE mining_request(store_id INT,request_id BIGINT,frozen_input JSON,utility_config_snapshot JSON,min_util_mode VARCHAR(30),min_util_value DECIMAL(20,6),min_util_spmf INT,input_snapshot_hash VARCHAR(64),utility_snapshot_id INT)");
        jdbc.execute("CREATE TABLE pattern_item(pattern_id BIGINT,store_id INT,product_id INT,algorithm_item_id INT)");
        jdbc.execute("CREATE TABLE transaction_item(product_id INT)");jdbc.update("INSERT INTO transaction_item VALUES (11)");
        json=new UtilitySettingsRepository(jdbc,JsonMapper.builder().findAndAddModules().addModule(vn.edu.fpt.paspm.utility.UtilityJsonVersions.module()).build());
        mining=mock(MiningRepository.class);snapshots=mock(UtilitySnapshotRepository.class);
        snapshot=new UtilityMatrixContracts.SnapshotView(3,7,1,LocalDate.of(2026,10,19),LocalDate.of(2026,10,19),LocalDate.of(2026,10,19),new UtilityMatrixContracts.FrozenConfiguration(UtilityContracts.WeightConfig.defaults(),null,null,LocalDate.of(2026,10,19),"TEST",6,"HALF_UP","PRODUCT_ID_V1","Asia/Ho_Chi_Minh"),"a".repeat(64),"b".repeat(64),BigDecimal.valueOf(2),6,"READY",Instant.EPOCH,List.of());
        input=new MiningInputBuilder.Dataset("1 2:2:2 0\n1:0:0\n",MiningInputBuilder.sha("1 2:2:2 0\n1:0:0\n"),Map.of(1,11,2,22),1000000,2,2);
        run=run("COMPLETED");when(mining.run(7,9)).thenReturn(run);when(snapshots.find(7,3)).thenReturn(snapshot);
        when(snapshots.countItems(3)).thenReturn(3L);
        when(snapshots.page(7,3,0,MiningInputBuilder.MAX_ROWS)).thenReturn(List.of(row(1,11),row(1,22),row(2,11)));
        when(mining.patterns(7,9,0,MiningInputBuilder.MAX_ROWS)).thenReturn(new MiningContracts.Page<>(List.of(new MiningContracts.PatternView(5,9,List.of(11,22),BigDecimal.valueOf(2),2,2,null)),0,MiningInputBuilder.MAX_ROWS,1));
        jdbc.update("INSERT INTO pattern_item VALUES (5,7,11,1),(5,7,22,2)");save();reader=new FrozenEvidenceReader(jdbc,mining,snapshots,json);
    }
    MiningContracts.RunView run(String state) {return run(state,1);}
    MiningContracts.RunView run(String state,Integer patternCount) {return new MiningContracts.RunView(9,4,7,3,MiningEngine.Algorithm.HUI_MINER,1,"test",state,1,input.hash(),MiningInputBuilder.VERSION,null,null,null,null,"test","test",null,patternCount,null,null);}
    UtilityMatrixContracts.MatrixItem row(int tx,int product){return new UtilityMatrixContracts.MatrixItem(tx,product,product,1,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"NONE",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,null,null,null,null);}
    void save(){jdbc.update("DELETE FROM mining_request");jdbc.update("INSERT INTO mining_request VALUES (7,4,?,?,'ABSOLUTE',1,1,?,3)",json.write(input),json.write(snapshot),input.hash());}
    void conflict(){assertEquals(409,assertThrows(ApiException.class,()->reader.read(7,9)).status());}
    @Test void readsFrozenMembershipIncludingZeroUtilityAndIgnoresNullableSpmfSupportAndLiveEdits(){
        String frozenJsonBefore=jdbc.queryForObject("SELECT frozen_input FROM mining_request WHERE store_id=7 AND request_id=4",String.class);
        var source=reader.read(7,9);assertEquals(frozenJsonBefore,jdbc.queryForObject("SELECT frozen_input FROM mining_request WHERE store_id=7 AND request_id=4",String.class));
        assertEquals(1,source.evidence().measure(List.of(11,22),11).supportCount());
        assertEquals(new BigDecimal("0.500000"),source.evidence().measure(List.of(11,22),11).confidence());
        jdbc.update("DELETE FROM transaction_item");assertEquals(2,reader.read(7,9).evidence().transactionCount());
        assertEquals(List.of(11,22),source.patterns().get(0).productIds());
    }
    @Test void readsFrozenEvidenceAtTheConfiguredFoodMartProductLimit(){
        int count=MiningInputBuilder.MAX_ITEMS;
        assertEquals(1600,count);
        var products=new LinkedHashMap<Integer,Integer>();var text=new StringBuilder();var rows=new ArrayList<UtilityMatrixContracts.MatrixItem>();
        for(int product=1;product<=count;product++){
            products.put(product,product);text.append(product).append(":1:1\n");rows.add(row(product,product));
        }
        input=new MiningInputBuilder.Dataset(text.toString(),MiningInputBuilder.sha(text.toString()),products,1000000,count,count);
        var date=snapshot.asOfDate();
        snapshot=new UtilityMatrixContracts.SnapshotView(3,7,1,date,date,date,snapshot.configuration(),"a".repeat(64),"b".repeat(64),BigDecimal.valueOf(count),6,"READY",Instant.EPOCH,List.of());
        run=run("COMPLETED",0);
        when(mining.run(7,9)).thenReturn(run);
        when(snapshots.find(7,3)).thenReturn(snapshot);
        when(snapshots.countItems(3)).thenReturn((long)count);
        when(snapshots.page(7,3,0,MiningInputBuilder.MAX_ROWS)).thenReturn(rows);
        when(mining.patterns(7,9,0,MiningInputBuilder.MAX_ROWS)).thenReturn(new MiningContracts.Page<>(List.of(),0,MiningInputBuilder.MAX_ROWS,0));
        jdbc.update("DELETE FROM pattern_item");save();

        var source=reader.read(7,9);

        assertEquals(count,source.evidence().transactionCount());
        assertEquals(count,source.snapshot().totalWeightedUtility().intValueExact());
    }
    @Test void readsNewFrozenEvidenceWithLongTotalExactly(){
        String text="1:1500000000:1500000000\n2:1500000000:1500000000\n3:1:1\n";
        input=new MiningInputBuilder.Dataset(text,MiningInputBuilder.sha(text),Map.of(1,11,2,22,3,33),1,3000000001L,3);
        var date=snapshot.asOfDate();
        snapshot=new UtilityMatrixContracts.SnapshotView(3,7,1,date,date,date,snapshot.configuration(),"a".repeat(64),"b".repeat(64),new BigDecimal("3000.000001"),6,"READY",Instant.EPOCH,List.of());
        run=run("COMPLETED",0);
        when(mining.run(7,9)).thenReturn(run);
        when(snapshots.find(7,3)).thenReturn(snapshot);
        when(snapshots.countItems(3)).thenReturn(3L);
        when(snapshots.page(7,3,0,MiningInputBuilder.MAX_ROWS)).thenReturn(List.of(row(1,11),row(2,22),row(3,33)));
        when(mining.patterns(7,9,0,MiningInputBuilder.MAX_ROWS)).thenReturn(new MiningContracts.Page<>(List.of(),0,MiningInputBuilder.MAX_ROWS,0));
        save();

        var source=reader.read(7,9);

        assertEquals(3000000001L,input.totalUtility());
        assertEquals(new BigDecimal("3000.000001"),input.originalUtility(input.totalUtility()));
        assertEquals(new BigDecimal("3000.000001"),source.snapshot().totalWeightedUtility());
        assertEquals(3,source.evidence().transactionCount());
    }
    @Test void rejectsTamperedLongFrozenTotal(){
        String text="1:1500000000:1500000000\n2:1500000000:1500000000\n3:1:1\n";
        input=new MiningInputBuilder.Dataset(text,MiningInputBuilder.sha(text),Map.of(1,11,2,22,3,33),1,3000000002L,3);
        var date=snapshot.asOfDate();
        snapshot=new UtilityMatrixContracts.SnapshotView(3,7,1,date,date,date,snapshot.configuration(),"a".repeat(64),"b".repeat(64),new BigDecimal("3000.000001"),6,"READY",Instant.EPOCH,List.of());
        run=run("COMPLETED",0);
        when(mining.run(7,9)).thenReturn(run);
        when(snapshots.find(7,3)).thenReturn(snapshot);
        when(snapshots.countItems(3)).thenReturn(3L);
        when(snapshots.page(7,3,0,MiningInputBuilder.MAX_ROWS)).thenReturn(List.of(row(1,11),row(2,22),row(3,33)));
        when(mining.patterns(7,9,0,MiningInputBuilder.MAX_ROWS)).thenReturn(new MiningContracts.Page<>(List.of(),0,MiningInputBuilder.MAX_ROWS,0));
        save();

        conflict();
    }
    @Test void rejectsHashMismatch(){jdbc.update("UPDATE mining_request SET input_snapshot_hash=?","c".repeat(64));conflict();}
    @Test void rejectsMissingOrWrongDenseMapping(){jdbc.update("UPDATE pattern_item SET algorithm_item_id=9 WHERE product_id=22");conflict();}
    @Test void rejectsForeignPatternItems(){jdbc.update("UPDATE pattern_item SET store_id=8 WHERE product_id=22");conflict();}
    @Test void rejectsSnapshotMismatch(){snapshot=new UtilityMatrixContracts.SnapshotView(3,7,1,snapshot.rangeStart(),snapshot.rangeEnd(),snapshot.asOfDate(),snapshot.configuration(),snapshot.sourceHash(),"c".repeat(64),BigDecimal.valueOf(2),6,"READY",Instant.EPOCH,List.of());when(snapshots.find(7,3)).thenReturn(snapshot);conflict();}
    @Test void rejectsFrozenMatrixMembershipMismatch(){when(snapshots.page(7,3,0,MiningInputBuilder.MAX_ROWS)).thenReturn(List.of(row(1,11),row(2,11),row(2,22)));conflict();}
    @Test void rejectsNonCompletedRun(){when(mining.run(7,9)).thenReturn(run("FAILED"));conflict();}
    @Test void missingOrForeignRunRemains404(){when(mining.run(8,9)).thenThrow(new ApiException(404,"Mining run not found"));assertEquals(404,assertThrows(ApiException.class,()->reader.read(8,9)).status());}
    @Test void completedZeroPatternsIsValid(){when(mining.run(7,9)).thenReturn(run("COMPLETED",0));when(mining.patterns(7,9,0,MiningInputBuilder.MAX_ROWS)).thenReturn(new MiningContracts.Page<>(List.of(),0,MiningInputBuilder.MAX_ROWS,0));assertTrue(reader.read(7,9).patterns().isEmpty());}
    @Test void rejectsCompletedRunWithAllRecordedPatternsMissing(){when(mining.patterns(7,9,0,MiningInputBuilder.MAX_ROWS)).thenReturn(new MiningContracts.Page<>(List.of(),0,MiningInputBuilder.MAX_ROWS,0));conflict();}
    @Test void rejectsCompletedRunWithPartiallyMissingPatterns(){when(mining.run(7,9)).thenReturn(run("COMPLETED",2));conflict();}
    @Test void rejectsCompletedRunWithNullRecordedPatternCount(){when(mining.run(7,9)).thenReturn(run("COMPLETED",null));conflict();}
    @Test void rejectsCompletedRunWithNegativeRecordedPatternCount(){when(mining.run(7,9)).thenReturn(run("COMPLETED",-1));conflict();}
    @Test void rejectsPatternWithNoActualCooccurrence(){
        String text="1:2:2\n2:0:0\n";
        input=new MiningInputBuilder.Dataset(text,MiningInputBuilder.sha(text),Map.of(1,11,2,22),1000000,2,2);
        when(mining.run(7,9)).thenReturn(run("COMPLETED"));save();
        when(snapshots.countItems(3)).thenReturn(2L);
        when(snapshots.page(7,3,0,MiningInputBuilder.MAX_ROWS)).thenReturn(List.of(row(1,11),row(2,22)));conflict();
    }
    // DEC-20261119-05: version dispatch for whole-đồng frozen inputs.
    UtilityMatrixContracts.MatrixItem weighted(int tx,int product,String value){var v=new BigDecimal(value);return new UtilityMatrixContracts.MatrixItem(tx,product,product,1,v,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"NONE",BigDecimal.ONE,v,v,null,null,null,null);}
    void wholeUnitFixture(long total,String version,String mode){
        String text="1:1073741823:1073741823\n1:1073741823:1073741823\n2:0:0\n";
        input=new MiningInputBuilder.Dataset(text,MiningInputBuilder.sha(text),Map.of(1,11,2,22),1,total,3,version,0,"HALF_UP",mode,BigDecimal.ONE);
        var date=snapshot.asOfDate();
        snapshot=new UtilityMatrixContracts.SnapshotView(3,7,1,date,date,date,snapshot.configuration(),"a".repeat(64),"b".repeat(64),new BigDecimal("2147483647.000000"),6,"READY",Instant.EPOCH,List.of());
        run=new MiningContracts.RunView(9,4,7,3,MiningEngine.Algorithm.EFIM,1,"test","COMPLETED",1,input.hash(),version,null,null,null,null,"test","test",null,0,null,null);
        when(mining.run(7,9)).thenReturn(run);when(snapshots.find(7,3)).thenReturn(snapshot);when(snapshots.countItems(3)).thenReturn(3L);
        when(snapshots.page(7,3,0,MiningInputBuilder.MAX_ROWS)).thenReturn(List.of(weighted(1,11,"1073741823.4"),weighted(2,11,"1073741823.4"),weighted(3,22,"0.2")));
        when(mining.patterns(7,9,0,MiningInputBuilder.MAX_ROWS)).thenReturn(new MiningContracts.Page<>(List.of(),0,MiningInputBuilder.MAX_ROWS,0));
        jdbc.update("DELETE FROM pattern_item");save();
    }
    @Test void readsWholeDongFrozenInputByItsVersionAgainstTheRoundedMatrixTotal(){
        wholeUnitFixture(2147483646L,MiningInputBuilder.WHOLE_UNIT_VERSION,MiningInputBuilder.MODE_WHOLE_UNIT);
        var source=reader.read(7,9);
        assertEquals("WHOLE_UNIT_HALF_UP",source.run().conversionMode());
        assertEquals(0,new BigDecimal("2147483647").compareTo(source.snapshot().totalWeightedUtility()),"the snapshot stays exact");
        assertEquals(0,BigDecimal.ONE.compareTo(source.effectiveMinUtility()),"threshold 1 is 1 đồng");
        assertEquals(3,source.evidence().transactionCount());
    }
    @Test void rejectsWholeDongTotalThatIsNotTheSumOfRoundedItems(){
        wholeUnitFixture(2147483647L,MiningInputBuilder.WHOLE_UNIT_VERSION,MiningInputBuilder.MODE_WHOLE_UNIT);
        conflict();
    }
    @Test void rejectsUnknownOrInconsistentConversionVersions(){
        wholeUnitFixture(2147483646L,"F03_FUTURE_CONVERSION_V9",MiningInputBuilder.MODE_WHOLE_UNIT);
        conflict();
        wholeUnitFixture(2147483646L,MiningInputBuilder.WHOLE_UNIT_VERSION,null);
        conflict();
    }
    @Test void malformedPersistedJsonIsSanitizedConflict(){jdbc.update("UPDATE mining_request SET frozen_input=?","{}");var error=assertThrows(ApiException.class,()->reader.read(7,9));assertEquals(409,error.status());assertFalse(error.getMessage().contains("SQL"));}
}
