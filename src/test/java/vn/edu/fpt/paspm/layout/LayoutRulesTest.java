package vn.edu.fpt.paspm.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.paspm.layout.LayoutContracts.PlacementBlock;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.RecType;

/** DEC-20261119-06: zone capacities, top-K baseline and equal-size comparison, without Spring. */
class LayoutRulesTest {
    static LayoutRules owner() {
        var properties=new LayoutPolicyProperties();
        properties.setMaxBlocksByZone(Map.of("BANNER",3,"BUNDLE",4,"CROSS_SELL",2,"FBT",2,"BASELINE",35));
        properties.setMaxProductsByZone(Map.of("BANNER",3,"BUNDLE",16,"CROSS_SELL",8,"FBT",8,"BASELINE",35));
        return new LayoutRules(properties);
    }
    static List<PlacementBlock> blocks(RecType type,int count,int productsPerBlock) {
        var result=new ArrayList<PlacementBlock>();int product=1;
        for(int block=1;block<=count;block++){
            var ids=new ArrayList<Integer>();for(int i=0;i<productsPerBlock;i++)ids.add(product++);
            result.add(new PlacementBlock((long)block,type,ids,type.name(),block,block));
        }
        return result;
    }

    @Test void everyZoneAcceptsItsBlockCapAndRejectsOneMore() {
        var rules=owner();
        Map<RecType,Integer> blockCaps=Map.of(RecType.BANNER,3,RecType.BUNDLE,4,RecType.CROSS_SELL,2,RecType.FBT,2);
        blockCaps.forEach((type,cap)->{
            assertEquals(List.of(),rules.capacityErrors(blocks(type,cap,1)),type+" at the block cap");
            var errors=rules.capacityErrors(blocks(type,cap+1,1));
            assertTrue(errors.contains(type.name()+" exceeds configured blocks capacity "+cap),type+" one block above: "+errors);
        });
    }

    @Test void everyZoneAcceptsItsProductCapAndRejectsOneMore() {
        var rules=owner();
        // BANNER 3 blocks x 1 = 3; BUNDLE 4 x 4 = 16; CROSS_SELL / FBT 2 x 4 = 8.
        assertEquals(List.of(),rules.capacityErrors(blocks(RecType.BANNER,3,1)));
        assertEquals(List.of(),rules.capacityErrors(blocks(RecType.BUNDLE,4,4)));
        assertEquals(List.of(),rules.capacityErrors(blocks(RecType.CROSS_SELL,2,4)));
        assertEquals(List.of(),rules.capacityErrors(blocks(RecType.FBT,2,4)));
        var bundle=blocks(RecType.BUNDLE,4,4);bundle.set(3,new PlacementBlock(4L,RecType.BUNDLE,List.of(13,14,15,16,17),"BUNDLE",4,4));
        assertTrue(rules.capacityErrors(bundle).get(0).contains("products"),"17 BUNDLE products");
        var cross=blocks(RecType.CROSS_SELL,2,4);cross.set(1,new PlacementBlock(2L,RecType.CROSS_SELL,List.of(5,6,7,8,9),"CROSS_SELL",2,2));
        assertTrue(rules.capacityErrors(cross).get(0).contains("products"),"9 CROSS_SELL products");
        var fbt=blocks(RecType.FBT,2,4);fbt.set(1,new PlacementBlock(2L,RecType.FBT,List.of(5,6,7,8,9),"FBT",2,2));
        assertTrue(rules.capacityErrors(fbt).get(0).contains("products"),"9 FBT products");
        var banner=blocks(RecType.BANNER,2,1);banner.add(new PlacementBlock(3L,RecType.BANNER,List.of(3,4),"BANNER",3,3));
        assertTrue(rules.capacityErrors(banner).stream().anyMatch(error->error.contains("products")),"4 BANNER products in 3 blocks");
    }

    @Test void baselineZoneAcceptsThirtyFiveAndRejectsThirtySix() {
        var rules=owner();
        var at=IntStream.rangeClosed(1,35).mapToObj(i->new PlacementBlock(1L,RecType.BANNER,List.of(i),"BASELINE",i,i)).toList();
        assertEquals(List.of(),rules.capacityErrors(at));
        var above=new ArrayList<>(at);above.add(new PlacementBlock(1L,RecType.BANNER,List.of(36),"BASELINE",36,36));
        assertEquals(2,rules.capacityErrors(above).size(),"36 blocks and 36 products");
    }

    @Test void baselineKeepsTheTopKInRankOrderOrEverythingWhenUnconfigured() {
        var ranked=IntStream.rangeClosed(1,40).boxed().toList();
        var rules=owner();
        assertEquals(35,rules.baselineK());
        assertEquals(ranked.subList(0,35),rules.baselineSelection(ranked));
        assertEquals(List.of(3,1),rules.baselineSelection(List.of(3,1)),"fewer products than K keeps all, order preserved");

        var unconfigured=new LayoutRules(new LayoutPolicyProperties());
        assertNull(unconfigured.baselineK());
        assertEquals(ranked,unconfigured.baselineSelection(ranked),"no BASELINE cap keeps the V1 all-products behaviour");
    }

    @Test void equalSizePrefixUsesTheFirstNProductsOfAOrAllOfAWhenNIsLarger() {
        var a=List.of(9,4,7,1,3);
        assertEquals(List.of(9,4),LayoutRules.equalSizePrefix(a,2));
        assertEquals(a,LayoutRules.equalSizePrefix(a,5));
        assertEquals(a,LayoutRules.equalSizePrefix(a,8),"N > |A| uses all of A");
        var exceeding=LayoutRules.equalSizeAssumptions(8,5,35);
        assertEquals("EQUAL_SIZE_TOP_N_OF_A",exceeding.get("comparisonMode"));
        assertEquals(8,exceeding.get("comparedProductCountN"));
        assertEquals(5,exceeding.get("baselineProductCount"));
        assertEquals(5,exceeding.get("baselineProductsCompared"));
        assertEquals(35,exceeding.get("baselineK"));
        assertEquals(true,exceeding.get("nExceedsBaseline"));
        var within=LayoutRules.equalSizeAssumptions(8,35,null);
        assertEquals(8,within.get("baselineProductsCompared"));
        assertEquals(false,within.get("nExceedsBaseline"));
        assertTrue(within.containsKey("baselineK"));assertNull(within.get("baselineK"),"V1 baseline has no K");
    }
}
