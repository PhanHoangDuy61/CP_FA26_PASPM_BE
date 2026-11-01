package vn.edu.fpt.paspm.mining;

import java.util.Map;

/** Isolated algorithm execution over one immutable, normalized dataset. */
public interface MiningEngine {
    enum Algorithm { HUI_MINER, EFIM }
    record Output(Map<String,Integer> patterns,long runtimeMillis,double memoryMiB,String environment) {}
    record ExecutionIdentity(int storeId,long requestId,long runId,String owner,long fence) {}
    Output execute(Algorithm algorithm,MiningInputBuilder.Dataset input,int minUtility) throws Exception;
    default Output execute(Algorithm algorithm,MiningInputBuilder.Dataset input,int minUtility,ExecutionIdentity identity) throws Exception {
        return execute(algorithm,input,minUtility);
    }
    void requireAvailable();
}
