package vn.edu.fpt.paspm.recommendation;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionEvidenceTest {
    @Test void countsPresenceAndDirectionalConfidenceFromSixTransactions() {
        var evidence=new TransactionEvidence(List.of(Set.of(1,2),Set.of(1,2),Set.of(1),Set.of(2),Set.of(2),Set.of(3)));
        var ab=evidence.measure(List.of(1,2,2),1);
        assertEquals(6,ab.totalTransactions());assertEquals(2,ab.supportCount());
        assertEquals(new BigDecimal("0.333333"),ab.supportRatio());
        assertEquals(3,ab.anchorSupportCount());assertEquals(new BigDecimal("0.666667"),ab.confidence());
        assertEquals(new BigDecimal("0.500000"),evidence.measure(List.of(1,2),2).confidence());
        assertEquals("AVAILABLE",ab.status());
    }
    @Test void copiesInputAndCountsRepeatedPresenceOnce() {
        var basket=new HashSet<>(List.of(1,1,2));var baskets=new ArrayList<Set<Integer>>();baskets.add(basket);
        var evidence=new TransactionEvidence(baskets);basket.clear();baskets.clear();
        assertEquals(1,evidence.measure(List.of(1,1),null).supportCount());assertEquals(1,evidence.transactionCount());
    }
    @Test void emptyEvidenceHasNoFabricatedCountsOrRatios() {
        var result=new TransactionEvidence(List.of()).measure(List.of(1,2),1);
        assertEquals("EVIDENCE_UNAVAILABLE",result.status());assertEquals(0,result.totalTransactions());
        assertNull(result.supportCount());assertNull(result.supportRatio());assertNull(result.anchorSupportCount());assertNull(result.confidence());
    }
    @Test void zeroAnchorHasUndefinedConfidence() {
        var result=new TransactionEvidence(List.of(Set.of(1))).measure(List.of(1,2),2);
        assertEquals("ZERO_ANCHOR_SUPPORT",result.status());assertEquals(0,result.supportCount());assertEquals(0,result.anchorSupportCount());assertNull(result.confidence());
    }
}
