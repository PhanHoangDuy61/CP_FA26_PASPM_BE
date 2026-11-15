package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.*;

/**
 * Single W2_EXPIRY_SEASON_V1 calculation path shared by preview, snapshot, product scores, scheduled snapshots and F06.
 * v(i) = units of COMPLETED, F03-eligible transactions dated within the N days ending on (and including) the
 * analysis date, in the Store time zone, divided by N.
 */
@Service
public class WeightV2Calculator {
    private final TransactionSource source;
    private final UtilityProductRepository products;
    private final ExpiryRiskCalculator risk;
    private final SeasonRepository seasons;
    public WeightV2Calculator(TransactionSource source,UtilityProductRepository products,ExpiryRiskCalculator risk,SeasonRepository seasons) {
        this.source=source;this.products=products;this.risk=risk;this.seasons=seasons;
    }
    public record SeasonScore(BigDecimal weight,Integer seasonId) { public static final SeasonScore NONE=new SeasonScore(BigDecimal.ZERO,null); }
    public record ProductWeight(int productId,ExpiryRiskCalculator.Result expiry,SeasonScore season,BigDecimal weight,List<String> warnings) {}

    public List<SeasonCopy> activeSeasons(int store,LocalDate analysisDate) { return seasons.active(store,analysisDate); }

    /** Units sold per product in the sales window ending on analysisDate (inclusive). */
    public Map<Integer,Long> unitsSold(int store,LocalDate analysisDate,int windowDays) {
        var from=analysisDate.minusDays(windowDays-1L);
        var units=new HashMap<Integer,Long>();
        for(var tx:source.load(store,from,analysisDate)) {
            if(tx.storeId()!=store||!tx.eligible()||tx.occurredOn()==null||tx.occurredOn().isBefore(from)||tx.occurredOn().isAfter(analysisDate))continue;
            for(var item:tx.items())units.merge(item.productId(),(long)item.quantity(),Long::sum);
        }
        return units;
    }
    /** P2: a product in several active seasons gets the highest weight; ties go to the lowest seasonId. */
    public static Map<Integer,SeasonScore> seasonScores(List<SeasonCopy> active) {
        var scores=new HashMap<Integer,SeasonScore>();
        var ordered=new ArrayList<>(active);ordered.sort(Comparator.comparingInt(SeasonCopy::seasonId));
        for(var season:ordered)for(int product:season.productIds())
            scores.merge(product,new SeasonScore(season.weight(),season.seasonId()),(a,b)->b.weight().compareTo(a.weight())>0?b:a);
        return scores;
    }
    public ProductWeight product(int store,UtilityProductRepository.Product product,LocalDate analysisDate,WeightV2 config,
            Map<Integer,Long> unitsSold,Map<Integer,SeasonScore> seasonScores) {
        var batches="ACTIVE".equals(product.status())?products.riskBatches(store,product.id()):List.<ExpiryRiskCalculator.Batch>of();
        var expiry=risk.calculate(product.requiresExpiry(),batches,analysisDate,config.salesWindowDays(),unitsSold.getOrDefault(product.id(),0L));
        var season=seasonScores.getOrDefault(product.id(),SeasonScore.NONE);
        var weight=UtilityMath.weightV2(config.lambda(),config.alpha(),config.beta(),expiry.riskScaled(),expiry.eligibleScaled(),season.weight());
        var warnings=new ArrayList<String>();
        if(!expiry.batchesMissingPushDay().isEmpty())warnings.add("Product "+product.id()+": batches "+expiry.batchesMissingPushDay()+" have no push day and are excluded from expiry risk");
        if("SLOW_MOVING".equals(expiry.status()))warnings.add("Product "+product.id()+": "+ExpiryRiskCalculator.SLOW_MOVING+" (no units sold in the last "+config.salesWindowDays()+" days; U_expiry = 0)");
        if("NO_SELLABLE_BATCH".equals(expiry.status()))warnings.add("Product "+product.id()+": no sellable batch; historical transactions retained");
        return new ProductWeight(product.id(),expiry,season,weight,List.copyOf(warnings));
    }
}
