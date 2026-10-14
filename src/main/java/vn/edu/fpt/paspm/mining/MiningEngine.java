package vn.edu.fpt.paspm.mining;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

/**
 * Future F05 adapter boundary; no mining implementation is claimed in F01.
 */
public interface MiningEngine {
    enum Algorithm {
        HUI_MINER,
        EFIM
    }

    record Input(Path immutableSnapshot, String sha256, BigDecimal minUtility, String implementationVersion) {
    }

    record Pattern(List<Integer> productIds, BigDecimal utility) {
    }

    record Output(List<Pattern> patterns, Path rawOutput, long runtimeMillis, Long peakMemoryBytes) {
    }

    Algorithm algorithm();

    Output execute(Input input) throws Exception;
}
