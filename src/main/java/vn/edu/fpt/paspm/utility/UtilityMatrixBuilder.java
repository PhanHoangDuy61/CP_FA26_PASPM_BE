package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.*;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.FrozenV2;

/** Builds W2_EXPIRY_SEASON_V1 matrices. Legacy snapshots are read from storage and never rebuilt. */
@Service
public class UtilityMatrixBuilder {
    private final TransactionSource source;
    private final UtilityProductRepository products;
    private final WeightV2Calculator weights;
    private final UtilitySettingsRepository json;
    public UtilityMatrixBuilder(TransactionSource source,UtilityProductRepository products,WeightV2Calculator weights,UtilitySettingsRepository json) {
        this.source=source;this.products=products;this.weights=weights;this.json=json;
    }
    public String sourceType() { return source.sourceType(); }
    public MatrixResult build(int store,RangeRequest range,FrozenV2 frozen) {
        if(range.rangeStart().isAfter(range.rangeEnd()))throw new ApiException(400,"rangeStart must not exceed rangeEnd");
        if(!range.asOfDate().equals(frozen.analysisDate()))throw new IllegalArgumentException("Frozen analysis date differs from the range");
        var transactions=new ArrayList<>(source.load(store,range.rangeStart(),range.rangeEnd()));
        transactions.sort(Comparator.comparingInt(TransactionSource.PreparedTransaction::transactionId));
        var unitsSold=weights.unitsSold(store,frozen.analysisDate(),frozen.config().salesWindowDays());
        var seasonScores=WeightV2Calculator.seasonScores(frozen.seasons());
        Set<Integer> transactionIds=new HashSet<>();
        Map<Integer,UtilityProductRepository.Product> productCache=new HashMap<>();
        Map<Integer,WeightV2Calculator.ProductWeight> weightCache=new HashMap<>();
        List<MatrixItem> rows=new ArrayList<>();
        Set<String> warnings=new TreeSet<>();
        StringBuilder sourceContent=new StringBuilder();
        for(var transaction:transactions) {
            if(transaction.storeId()!=store)throw new ApiException(400,"Prepared transaction provider violated Store isolation");
            if(transaction.occurredOn()==null)throw new ApiException(400,"Prepared transaction date is required");
            if(!transaction.eligible()||transaction.occurredOn().isBefore(range.rangeStart())||transaction.occurredOn().isAfter(range.rangeEnd()))continue;
            if(transaction.transactionId()<=0||!transactionIds.add(transaction.transactionId()))throw new ApiException(400,"Duplicate or invalid prepared transaction ID");
            if(transaction.items()==null||transaction.items().isEmpty())throw new ApiException(400,"Eligible prepared transaction has no items");
            sourceContent.append(store).append('|').append(transaction.transactionId()).append('|').append(transaction.occurredOn()).append('\n');
            var items=new ArrayList<>(transaction.items());items.sort(Comparator.comparingInt(TransactionSource.Item::productId));
            Set<Integer> productIds=new HashSet<>();
            List<MatrixItem> transactionRows=new ArrayList<>();
            BigDecimal total=BigDecimal.ZERO;
            for(var item:items) {
                if(item.productId()<=0||!productIds.add(item.productId()))throw new ApiException(400,"Duplicate or invalid product in prepared transaction; F04 must preprocess duplicate items");
                var product=productCache.computeIfAbsent(item.productId(),id->products.product(store,id));
                if(!"ACTIVE".equals(product.status()))warnings.add("Product "+product.id()+": inactive Product; historical transactions retained, not currently sellable");
                var weight=weightCache.computeIfAbsent(product.id(),id->weights.product(store,product,frozen.analysisDate(),frozen.config(),unitsSold,seasonScores));
                warnings.addAll(weight.warnings());
                BigDecimal base=UtilityMath.base(item.quantity(),item.unitPrice(),item.unitCost(),item.sourceUtility());
                sourceContent.append(item.productId()).append('|').append(item.quantity()).append('|').append(number(item.unitPrice())).append('|').append(number(item.unitCost())).append('|').append(number(item.sourceUtility())).append('\n');
                BigDecimal weighted=UtilityMath.weighted(base,weight.weight());total=total.add(weighted);
                transactionRows.add(new MatrixItem(transaction.transactionId(),product.id(),product.id(),item.quantity(),base,null,null,null,
                    weight.expiry().status(),weight.weight(),weighted,null,weight.expiry().evidence(),weight.expiry().score(),weight.season().weight(),weight.season().seasonId()));
            }
            BigDecimal transactionTotal=UtilityMath.utility(total);
            for(var row:transactionRows) rows.add(new MatrixItem(row.transactionId(),row.productId(),row.algorithmItemId(),row.quantity(),row.baseUtility(),row.stockScore(),row.expiryScore(),row.seasonalScore(),row.expiryStatus(),row.weightValue(),row.weightedUtility(),transactionTotal,row.expiryEvidence(),row.expiryRisk(),row.seasonWeight(),row.seasonId()));
        }
        BigDecimal total=UtilityMath.utility(rows.stream().map(MatrixItem::weightedUtility).reduce(BigDecimal.ZERO,BigDecimal::add));
        String sourceHash=hash(sourceContent.toString());
        String matrixHash=hash(json.write(frozen)+"\n"+range.rangeStart()+"|"+range.rangeEnd()+"\n"+sourceHash+"\n"+json.write(rows));
        // Missing push days and slow movers only warn (Q1/Q7); they never block snapshot creation.
        return new MatrixResult(List.copyOf(rows),total,sourceHash,matrixHash,List.copyOf(warnings),true);
    }
    private static String number(BigDecimal value) { return value==null?"null":value.stripTrailingZeros().toPlainString(); }
    private static String hash(String content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
