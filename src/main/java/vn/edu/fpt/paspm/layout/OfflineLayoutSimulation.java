package vn.edu.fpt.paspm.layout;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import vn.edu.fpt.paspm.utility.UtilityMath;

@Service
public class OfflineLayoutSimulation {
    public record Metrics(int hitCount, int denominator, int bundleConversionCount,
            BigDecimal capturedUtilityNumerator, BigDecimal capturedUtilityDenominator) {}

    public record Outcome(String datasetHash, int evaluationCount, Metrics layoutA, Metrics layoutB) {}

    private final LayoutCampaignRepository repository;

    public OfflineLayoutSimulation(LayoutCampaignRepository repository) {
        this.repository = repository;
    }

    public Outcome evaluate(int storeId, int snapshotId, Timestamp from, Timestamp to,
            Set<Integer> productsA, Set<Integer> productsB, List<Set<Integer>> bundleBlocksB,
            String splitDescription) {
        MessageDigest digest = sha256();
        update(digest, "F07_F08_OFFLINE_REPLAY_V1|" + storeId + "|" + snapshotId + "|" + from.toInstant()
                + "|" + to.toInstant() + "|" + splitDescription + "\n");
        var accumulator = new Accumulator(productsA, productsB, bundleBlocksB);
        final TransactionItems[] current = {null};
        repository.evaluationRows(storeId, snapshotId, from, to, rs -> {
            int transactionId = rs.getInt("transaction_id");
            Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();
            if (current[0] != null && current[0].transactionId != transactionId) {
                accumulator.accept(current[0]);
                current[0] = null;
            }
            if (current[0] == null) current[0] = new TransactionItems(transactionId, occurredAt);
            int productId = rs.getInt("product_id");
            int quantity = rs.getInt("quantity");
            BigDecimal unitPrice = rs.getBigDecimal("unit_price");
            BigDecimal unitCost = rs.getBigDecimal("unit_cost");
            BigDecimal baseUtility = UtilityMath.base(quantity, unitPrice, unitCost, null);
            current[0].add(productId, baseUtility);
            update(digest, transactionId + "|" + occurredAt + "|" + productId + "|" + quantity + "|"
                    + unitPrice.toPlainString() + "|" + unitCost.toPlainString() + "\n");
        });
        if (current[0] != null) accumulator.accept(current[0]);
        return new Outcome(HexFormat.of().formatHex(digest.digest()), accumulator.count,
                accumulator.metricsA(), accumulator.metricsB());
    }

    private static final class TransactionItems {
        private final int transactionId;
        private final Instant occurredAt;
        private final List<Integer> productIds = new ArrayList<>();
        private final List<ItemUtility> utilities = new ArrayList<>();

        private TransactionItems(int transactionId, Instant occurredAt) {
            this.transactionId = transactionId;
            this.occurredAt = occurredAt;
        }

        private void add(int productId, BigDecimal baseUtility) {
            productIds.add(productId);
            utilities.add(new ItemUtility(productId, baseUtility));
        }
    }

    private record ItemUtility(int productId, BigDecimal baseUtility) {}

    private static final class Accumulator {
        private final Set<Integer> productsA;
        private final Set<Integer> productsB;
        private final List<Set<Integer>> bundleBlocksB;
        private int count;
        private int hitsA;
        private int hitsB;
        private int bundleHitsB;
        private BigDecimal capturedA = BigDecimal.ZERO;
        private BigDecimal capturedB = BigDecimal.ZERO;
        private BigDecimal totalUtility = BigDecimal.ZERO;

        private Accumulator(Set<Integer> productsA, Set<Integer> productsB, List<Set<Integer>> bundleBlocksB) {
            this.productsA = productsA;
            this.productsB = productsB;
            this.bundleBlocksB = bundleBlocksB;
        }

        private void accept(TransactionItems transaction) {
            count++;
            var purchased = Set.copyOf(transaction.productIds);
            boolean hitA = false;
            boolean hitB = false;
            for (ItemUtility item : transaction.utilities) {
                totalUtility = totalUtility.add(item.baseUtility());
                if (productsA.contains(item.productId())) {
                    hitA = true;
                    capturedA = capturedA.add(item.baseUtility());
                }
                if (productsB.contains(item.productId())) {
                    hitB = true;
                    capturedB = capturedB.add(item.baseUtility());
                }
            }
            if (hitA) hitsA++;
            if (hitB) hitsB++;
            if (bundleBlocksB.stream().anyMatch(purchased::containsAll)) bundleHitsB++;
        }

        private Metrics metricsA() {
            return new Metrics(hitsA, count, 0, capturedA, totalUtility);
        }

        private Metrics metricsB() {
            return new Metrics(hitsB, count, bundleHitsB, capturedB, totalUtility);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for simulation dataset provenance", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }
}
