package vn.edu.fpt.paspm.layout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.RecType;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.RecView;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.ReviewStatus;
import vn.edu.fpt.paspm.recommendation.RecommendationService;
import vn.edu.fpt.paspm.utility.UtilitySnapshotService;
import static vn.edu.fpt.paspm.layout.LayoutContracts.*;
import static vn.edu.fpt.paspm.layout.LayoutCampaignRepository.*;

@Service
public class LayoutCampaignService {
    private static final String SOURCE_NAMESPACE = "F04_DATABASE_V1";
    private static final String SIMULATION_VERSION = "OFFLINE_REPLAY_V1";
    private static final int PAGE_MAX = 100;

    private final CatalogAccess access;
    private final UtilitySnapshotService snapshots;
    private final RecommendationService recommendations;
    private final LayoutCampaignRepository repository;
    private final LayoutRules rules;
    private final OfflineLayoutSimulation simulation;
    private final ObjectMapper json;
    private final ZoneId storeZone;

    public LayoutCampaignService(CatalogAccess access, UtilitySnapshotService snapshots,
            RecommendationService recommendations, LayoutCampaignRepository repository,
            LayoutRules rules, OfflineLayoutSimulation simulation, ObjectMapper json,
            @Value("${paspm.store-time-zone:Asia/Ho_Chi_Minh}") String storeTimeZone) {
        this.access = access;
        this.snapshots = snapshots;
        this.recommendations = recommendations;
        this.repository = repository;
        this.rules = rules;
        this.simulation = simulation;
        this.json = json;
        this.storeZone = ZoneId.of(storeTimeZone);
    }

    @Transactional(readOnly = true)
    public Page<LayoutSummary> layouts(Jwt jwt, Long campaignId, int page, int pageSize) {
        pagination(page, pageSize);
        int store = access.currentStoreId(jwt);
        if (campaignId != null) repository.campaign(store, campaignId, false);
        List<LayoutSummary> rows = repository.layouts(store, campaignId, page, pageSize).stream()
                .map(LayoutCampaignService::summary).toList();
        return new Page<>(rows, page, pageSize, repository.countLayouts(store, campaignId));
    }

    @Transactional(readOnly = true)
    public LayoutView layout(Jwt jwt, long layoutId) {
        int store = access.currentStoreId(jwt);
        return layoutView(jwt, repository.layout(store, layoutId, false));
    }

    @Transactional(readOnly = true)
    public LayoutView preview(Jwt jwt, long layoutId) {
        return layout(jwt, layoutId);
    }

    @Transactional(readOnly = true)
    public LayoutPolicyView policy() {
        return rules.policy();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<RecommendationOption> recommendationOptions(Jwt jwt, int snapshotId, int page, int pageSize) {
        pagination(page, pageSize);
        int store = access.currentStoreId(jwt);
        snapshots.get(jwt, snapshotId);
        var ids = new ArrayList<Long>();
        var first = recommendations.list(jwt, null, snapshotId, null, ReviewStatus.APPROVED, 0, PAGE_MAX);
        for (var item : first.items()) ids.add(item.recommendationId());
        for (int p = 1; (long) p * PAGE_MAX < first.totalElements(); p++) {
            var next = recommendations.list(jwt, null, snapshotId, null, ReviewStatus.APPROVED, p, PAGE_MAX);
            next.items().forEach(item -> ids.add(item.recommendationId()));
        }
        var eligible = new ArrayList<RecommendationOption>();
        for (Long id : ids) {
            RecView current = recommendations.get(jwt, id);
            if (current.snapshotId() == snapshotId && current.reviewStatus() == ReviewStatus.APPROVED
                    && current.readyForConsumption()) {
                eligible.add(new RecommendationOption(current.recommendationId(), current.snapshotId(),
                        current.type(), current.productIds(), current.utility(), current.version()));
            }
        }
        eligible.sort(Comparator.comparingLong(RecommendationOption::recommendationId));
        int start = (int) Math.min((long) page * pageSize, eligible.size());
        int end = Math.min(start + pageSize, eligible.size());
        return new Page<>(List.copyOf(eligible.subList(start, end)), page, pageSize, eligible.size());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CampaignView createCampaign(Jwt jwt, CreateCampaignRequest request) {
        int store = access.currentStoreId(jwt);
        int actor = actorId(jwt);
        snapshots.get(jwt, request.snapshotId());
        List<Integer> baseline = repository.baselineProducts(store, request.snapshotId());
        if (baseline.isEmpty()) throw new ApiException(400, "A campaign requires a nonempty READY utility snapshot");
        List<PlacementBlock> baselineBlocks = baselineBlocks(baseline);
        rules.requireCapacity(baselineBlocks);
        List<PlacementBlock> blocks = recommendationBlocks(jwt, request.snapshotId(), request.recommendationIds());
        rules.requireCapacity(blocks);

        repository.lockStore(store);
        long campaignId = repository.createCampaign(store, request.snapshotId(), request.name(), actor);
        repository.history(store, campaignId, null, CampaignStatus.DRAFT, actor, null, Map.of("created", true));
        String baselineParams = writeJson(Map.of("ruleVersion", "F07_BASELINE_V1",
                "ordering", "COUNT(DISTINCT transaction_id) DESC, product_id ASC",
                "score", "number of snapshot transactions containing each product"));
        long layoutAId = repository.insertLayout(store, campaignId, request.snapshotId(), LayoutRole.A,
                LayoutStatus.READY, "A Baseline", baselineParams, actor);
        long layoutBId = repository.insertLayout(store, campaignId, request.snapshotId(), LayoutRole.B,
                LayoutStatus.DRAFT, "B Recommendations", null, actor);
        repository.insertBaselinePlacements(store, layoutAId, request.snapshotId(), baseline);
        repository.replacePlacements(store, layoutBId, request.snapshotId(), blocks);
        repository.setCampaignLayouts(store, campaignId, layoutAId, layoutBId);
        saveInitialRevision(store, layoutAId, actor);
        saveInitialRevision(store, layoutBId, actor);
        return campaignView(jwt, repository.campaign(store, campaignId, false));
    }

    @Transactional(readOnly = true)
    public Page<CampaignView> campaigns(Jwt jwt, int page, int pageSize) {
        pagination(page, pageSize);
        int store = access.currentStoreId(jwt);
        List<CampaignView> rows = repository.campaigns(store, page, pageSize).stream()
                .map(row -> campaignView(jwt, row)).toList();
        return new Page<>(rows, page, pageSize, repository.countCampaigns(store));
    }

    @Transactional(readOnly = true)
    public CampaignView campaign(Jwt jwt, long campaignId) {
        int store = access.currentStoreId(jwt);
        return campaignView(jwt, repository.campaign(store, campaignId, false));
    }

    @Transactional
    public LayoutView savePlacements(Jwt jwt, long layoutId, SavePlacementsRequest request) {
        int store = access.currentStoreId(jwt);
        LayoutRow observed = repository.layout(store, layoutId, false);
        CampaignRow campaign = repository.campaign(store, observed.campaignId(), true);
        LayoutRow layout = repository.layout(store, layoutId, true);
        requireDraft(campaign);
        requireB(layout);
        requireVersion(layout.version(), request.expectedVersion(), "Layout version is stale");
        List<PlacementBlock> blocks = recommendationBlocks(jwt, layout.snapshotId(), request.recommendationIds());
        requireUniqueProducts(blocks);
        rules.requireCapacity(blocks);

        repository.updateLayoutVersion(store, layoutId, layout.version());
        repository.updateLayoutState(store, layoutId, LayoutStatus.DRAFT);
        repository.replacePlacements(store, layoutId, layout.snapshotId(), blocks);
        List<PlacementRow> saved = repository.placements(store, layoutId);
        repository.saveRevision(store, layoutId, layout.version() + 1, saved, actorId(jwt), hash(saved));
        repository.clearCampaignDecision(store, campaign.id());
        return layoutView(jwt, repository.layout(store, layoutId, false));
    }

    @Transactional
    public LayoutView readyLayout(Jwt jwt, long layoutId, TransitionRequest request) {
        int store = access.currentStoreId(jwt);
        LayoutRow observed = repository.layout(store, layoutId, false);
        CampaignRow campaign = repository.campaign(store, observed.campaignId(), true);
        LayoutRow layout = repository.layout(store, layoutId, true);
        requireDraft(campaign);
        requireB(layout);
        requireVersion(layout.version(), request.expectedVersion(), "Layout version is stale");
        LayoutView current = layoutView(jwt, layout);
        if (!current.valid()) throw new ApiException(409, "Layout B contains invalid blocks or exceeds configured capacity");
        if (layout.status() == LayoutStatus.READY) return current;
        repository.updateLayoutState(store, layoutId, LayoutStatus.READY);
        return layoutView(jwt, repository.layout(store, layoutId, false));
    }

    @Transactional(readOnly = true)
    public Page<CampaignReportView> reports(Jwt jwt, long campaignId, int page, int pageSize) {
        pagination(page, pageSize);
        int store = access.currentStoreId(jwt);
        repository.campaign(store, campaignId, false);
        List<CampaignReportView> rows = repository.reports(store, campaignId, page, pageSize).stream()
                .map(row -> reportView(store, row)).toList();
        return new Page<>(rows, page, pageSize, repository.countReports(store, campaignId));
    }

    @Transactional(readOnly = true)
    public CampaignReportView report(Jwt jwt, long reportId) {
        int store = access.currentStoreId(jwt);
        return reportView(store, repository.report(store, reportId));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CampaignReportView simulate(Jwt jwt, long campaignId, SimulationRequest request) {
        int store = access.currentStoreId(jwt);
        int actor = actorId(jwt);
        repository.lockStore(store);
        CampaignRow campaign = repository.campaign(store, campaignId, true);
        if (campaign.status() != CampaignStatus.DRAFT) throw new ApiException(409, "Only DRAFT campaigns can be simulated");
        requireVersion(campaign.version(), request.expectedCampaignVersion(), "Campaign version is stale");
        LayoutRow layoutA = repository.layout(store, campaign.layoutAId(), true);
        LayoutRow layoutB = repository.layout(store, campaign.layoutBId(), true);
        requireVersion(layoutA.version(), request.expectedLayoutAVersion(), "Layout A version is stale");
        requireVersion(layoutB.version(), request.expectedLayoutBVersion(), "Layout B version is stale");
        if (layoutA.role() != LayoutRole.A || layoutA.status() != LayoutStatus.READY
                || layoutB.role() != LayoutRole.B || layoutB.status() != LayoutStatus.READY) {
            throw new ApiException(409, "Both layouts must be READY before offline simulation");
        }
        LayoutView viewA = layoutView(jwt, layoutA);
        LayoutView viewB = layoutView(jwt, layoutB);
        if (!viewA.valid() || !viewB.valid()) throw new ApiException(409, "Both layouts must be currently valid before simulation");
        if (request.rangeStart().isAfter(request.rangeEnd())) {
            throw new ApiException(400, "rangeStart must not exceed rangeEnd");
        }
        var snapshot = snapshots.get(jwt, campaign.snapshotId());
        if (!request.rangeStart().isAfter(snapshot.rangeEnd())) {
            throw new ApiException(400, "Simulation rangeStart must be after the utility snapshot rangeEnd");
        }

        var placementsA = repository.placements(store, layoutA.id());
        var placementsB = repository.placements(store, layoutB.id());
        Set<Integer> productsA = productSet(placementsA);
        Set<Integer> productsB = productSet(placementsB);
        List<Set<Integer>> bundleBlocks = bundleBlocks(placementsB);
        Timestamp from = Timestamp.from(request.rangeStart().atStartOfDay(storeZone).toInstant());
        Timestamp to = Timestamp.from(request.rangeEnd().plusDays(1).atStartOfDay(storeZone).toInstant());
        String split = "F04 completed and F03-eligible transactions strictly after the utility snapshot rangeEnd; exclude every transaction_id in utility_matrix_item for this snapshot";
        OfflineLayoutSimulation.Outcome outcome = simulation.evaluate(store, campaign.snapshotId(), from, to,
                productsA, productsB, bundleBlocks, split);
        if (outcome.evaluationCount() < 0) throw new IllegalStateException("Negative evaluation count");
        var assumptions = new LinkedHashMap<String, Object>();
        assumptions.put("label", "simulated");
        assumptions.put("interpretation", "Historical replay measures fit to observed demand; it is not a real-user A/B test and does not establish causal impact, forecast revenue, or actual profit.");
        assumptions.put("trainTestSplit", split);
        assumptions.put("duplicateCartRule", "Transactions are distinct by transaction ID; identical cart contents are retained.");
        assumptions.put("baseUtility", "F03 UtilityMath.base: (unit_price - unit_cost) * quantity, rounded HALF_UP to 6 decimals.");
        assumptions.put("capturedUtilityNumerator", "Sum of base utility for purchased product lines whose product is in the layout.");
        assumptions.put("capturedUtilityDenominator", "Sum of base utility for every product line in the evaluation transactions.");
        assumptions.put("zeroDenominator", "rate is null (N/A) when its denominator is zero.");
        assumptions.put("bundleConversion", "A transaction converts when it contains every product in at least one displayed recommendation block of type BUNDLE; FBT is not counted as BUNDLE.");
        assumptions.put("priorityCoverage", "N/A until the F03 priority coverage formula is confirmed.");

        long reportId = repository.createReport(store, campaignId, request.rangeStart(), request.rangeEnd(),
                outcome.datasetHash(), SOURCE_NAMESPACE, outcome.evaluationCount(), SIMULATION_VERSION,
                assumptions, actor);
        repository.insertReportLayout(reportId, store, layoutA.id(), layoutA.version(), LayoutRole.A,
                outcome.layoutA().hitCount(), outcome.layoutA().denominator(), outcome.layoutA().bundleConversionCount(),
                outcome.layoutA().denominator(), outcome.layoutA().capturedUtilityNumerator(),
                outcome.layoutA().capturedUtilityDenominator());
        repository.insertReportLayout(reportId, store, layoutB.id(), layoutB.version(), LayoutRole.B,
                outcome.layoutB().hitCount(), outcome.layoutB().denominator(), outcome.layoutB().bundleConversionCount(),
                outcome.layoutB().denominator(), outcome.layoutB().capturedUtilityNumerator(),
                outcome.layoutB().capturedUtilityDenominator());
        repository.insertEvaluationTransactionIds(reportId, store, campaign.snapshotId(), from, to);
        return reportView(store, repository.report(store, reportId));
    }

    @Transactional
    public CampaignView decide(Jwt jwt, long campaignId, DecisionRequest request) {
        int store = access.currentStoreId(jwt);
        CampaignRow campaign = repository.campaign(store, campaignId, true);
        requireDraft(campaign);
        requireVersion(campaign.version(), request.expectedVersion(), "Campaign version is stale");
        long selectedId = request.decision() == CampaignDecision.KEEP_A ? requireLayoutId(campaign.layoutAId())
                : requireLayoutId(campaign.layoutBId());
        LayoutRow selected = repository.layout(store, selectedId, true);
        requireSelectedLayout(jwt, selected);
        if (request.reportId() != null) requireCurrentReport(store, campaign, request.reportId());
        repository.updateCampaignDecision(store, campaignId, campaign.version(), request.decision(),
                selectedId, request.reportId());
        repository.history(store, campaignId, CampaignStatus.DRAFT.name(), CampaignStatus.DRAFT,
                actorId(jwt), null, Map.of("decision", request.decision().name(), "reportId",
                        request.reportId() == null ? "" : request.reportId()));
        return campaignView(jwt, repository.campaign(store, campaignId, false));
    }

    @Transactional
    public CampaignView publish(Jwt jwt, long campaignId, TransitionRequest request) {
        int store = access.currentStoreId(jwt);
        CampaignRow campaign = repository.campaign(store, campaignId, true);
        requireVersion(campaign.version(), request.expectedVersion(), "Campaign version is stale");
        if (campaign.status() != CampaignStatus.DRAFT || campaign.decision() == null
                || campaign.selectedLayoutId() == null) {
            throw new ApiException(409, "DRAFT campaign requires a recorded A/B decision before publish");
        }
        LayoutRow selected = repository.layout(store, campaign.selectedLayoutId(), true);
        requireSelectedLayout(jwt, selected);
        if (campaign.decisionReportId() != null) requireCurrentReport(store, campaign, campaign.decisionReportId());
        repository.transitionCampaign(store, campaignId, campaign.version(), CampaignStatus.DRAFT,
                CampaignStatus.PUBLISHED, actorId(jwt));
        repository.history(store, campaignId, CampaignStatus.DRAFT.name(), CampaignStatus.PUBLISHED,
                actorId(jwt), null, Map.of("selectedLayoutId", selected.id()));
        return campaignView(jwt, repository.campaign(store, campaignId, false));
    }

    @Transactional
    public CampaignView withdraw(Jwt jwt, long campaignId, TransitionRequest request) {
        int store = access.currentStoreId(jwt);
        CampaignRow campaign = repository.campaign(store, campaignId, true);
        requireVersion(campaign.version(), request.expectedVersion(), "Campaign version is stale");
        if (campaign.status() != CampaignStatus.PUBLISHED) throw new ApiException(409, "Only PUBLISHED campaigns can be withdrawn");
        repository.withdrawCampaign(store, campaignId, campaign.version());
        repository.history(store, campaignId, CampaignStatus.PUBLISHED.name(), CampaignStatus.DRAFT,
                actorId(jwt), null, Map.of("withdrawn", true));
        return campaignView(jwt, repository.campaign(store, campaignId, false));
    }

    @Transactional
    public CampaignView activate(Jwt jwt, long campaignId, TransitionRequest request) {
        int store = access.currentStoreId(jwt);
        int actor = actorId(jwt);
        repository.lockStore(store);
        CampaignRow campaign = repository.campaign(store, campaignId, true);
        requireVersion(campaign.version(), request.expectedVersion(), "Campaign version is stale");
        if (campaign.status() != CampaignStatus.PUBLISHED || campaign.selectedLayoutId() == null) {
            throw new ApiException(409, "Only a published campaign with a selected layout can be activated");
        }
        LayoutRow selected = repository.layout(store, campaign.selectedLayoutId(), true);
        requireSelectedLayout(jwt, selected);
        if (campaign.decisionReportId() != null) requireCurrentReport(store, campaign, campaign.decisionReportId());
        CampaignRow previous = repository.activeCampaign(store, true);
        Long previousId = previous == null ? null : previous.id();
        if (previous != null && previous.id() != campaignId) {
            repository.transitionCampaign(store, previous.id(), previous.version(), CampaignStatus.ACTIVE,
                    CampaignStatus.ENDED, actor);
            repository.history(store, previous.id(), CampaignStatus.ACTIVE.name(), CampaignStatus.ENDED,
                    actor, repository.previousActiveCampaign(store, previous.id()), Map.of("replacedBy", campaignId));
        }
        repository.transitionCampaign(store, campaignId, campaign.version(), CampaignStatus.PUBLISHED,
                CampaignStatus.ACTIVE, actor);
        repository.history(store, campaignId, CampaignStatus.PUBLISHED.name(), CampaignStatus.ACTIVE,
                actor, previousId, Map.of("selectedLayoutId", selected.id()));
        return campaignView(jwt, repository.campaign(store, campaignId, false));
    }

    @Transactional
    public CampaignView end(Jwt jwt, long campaignId, TransitionRequest request) {
        int store = access.currentStoreId(jwt);
        int actor = actorId(jwt);
        repository.lockStore(store);
        CampaignRow campaign = repository.campaign(store, campaignId, true);
        requireVersion(campaign.version(), request.expectedVersion(), "Campaign version is stale");
        if (campaign.status() != CampaignStatus.ACTIVE) throw new ApiException(409, "Only ACTIVE campaigns can be ended");
        repository.transitionCampaign(store, campaignId, campaign.version(), CampaignStatus.ACTIVE,
                CampaignStatus.ENDED, actor);
        repository.history(store, campaignId, CampaignStatus.ACTIVE.name(), CampaignStatus.ENDED,
                actor, null, Map.of("ended", true));
        return campaignView(jwt, repository.campaign(store, campaignId, false));
    }

    @Transactional
    public ActiveLayoutView rollback(Jwt jwt, long campaignId, TransitionRequest request) {
        int store = access.currentStoreId(jwt);
        int actor = actorId(jwt);
        repository.lockStore(store);
        CampaignRow current = repository.campaign(store, campaignId, true);
        requireVersion(current.version(), request.expectedVersion(), "Campaign version is stale");
        if (current.status() != CampaignStatus.ACTIVE) throw new ApiException(409, "Rollback starts from the currently ACTIVE campaign");
        Long previousId = repository.previousActiveCampaign(store, campaignId);
        if (previousId == null) {
            repository.transitionCampaign(store, campaignId, current.version(), CampaignStatus.ACTIVE,
                    CampaignStatus.ENDED, actor);
            repository.history(store, campaignId, CampaignStatus.ACTIVE.name(), CampaignStatus.ENDED,
                    actor, null, Map.of("rollback", "DEFAULT_LAYOUT"));
            return activeLayout(jwt);
        }
        CampaignRow previous = repository.campaign(store, previousId, true);
        if (previous.status() != CampaignStatus.ENDED || previous.selectedLayoutId() == null) {
            throw new ApiException(409, "Previously active campaign cannot be restored from its current state");
        }
        requireSelectedLayout(jwt, repository.layout(store, previous.selectedLayoutId(), true));
        repository.transitionCampaign(store, campaignId, current.version(), CampaignStatus.ACTIVE,
                CampaignStatus.ENDED, actor);
        repository.history(store, campaignId, CampaignStatus.ACTIVE.name(), CampaignStatus.ENDED,
                actor, previous.id(), Map.of("rollbackTarget", previous.id()));
        repository.transitionCampaign(store, previous.id(), previous.version(), CampaignStatus.ENDED,
                CampaignStatus.ACTIVE, actor);
        repository.history(store, previous.id(), CampaignStatus.ENDED.name(), CampaignStatus.ACTIVE,
                actor, current.id(), Map.of("rollbackFrom", current.id()));
        return activeLayout(jwt);
    }

    @Transactional(readOnly = true)
    public ActiveLayoutView activeLayout(Jwt jwt) {
        int store = access.currentStoreId(jwt);
        CampaignRow campaign = repository.activeCampaign(store, false);
        if (campaign == null) return new ActiveLayoutView(true, null, null);
        if (campaign.selectedLayoutId() == null) throw new IllegalStateException("Active campaign has no selected layout");
        LayoutRow layout = repository.layout(store, campaign.selectedLayoutId(), false);
        return new ActiveLayoutView(false, summary(campaign), layoutView(jwt, layout));
    }

    private LayoutView layoutView(Jwt jwt, LayoutRow layout) {
        List<PlacementRow> rows = repository.placements(layout.storeId(), layout.id());
        Map<Integer, BigDecimal> utilities = repository.weightedUtilityByProduct(layout.snapshotId());
        Map<Long, List<String>> recommendationErrors = recommendationErrors(jwt, layout, rows);
        var errors = new LinkedHashSet<String>();
        var views = new ArrayList<PlacementView>();
        var distinctProducts = new HashSet<Integer>();
        if (layout.role() == LayoutRole.B && rows.isEmpty()) {
            errors.add("Layout B must contain at least one recommendation block");
        }
        for (PlacementRow row : rows) {
            var rowErrors = new ArrayList<String>();
            if (!distinctProducts.add(row.productId())) rowErrors.add("Product is repeated in this layout");
            if (row.recommendationId() != null) {
                rowErrors.addAll(recommendationErrors.getOrDefault(row.recommendationId(), List.of()));
            }
            BigDecimal utility = utilities.get(row.productId());
            if (utility == null) rowErrors.add("Product is not present in the selected utility snapshot");
            errors.addAll(rowErrors);
            views.add(new PlacementView(row.productId(), row.recommendationId(),
                    row.type() == null ? null : RecType.valueOf(row.type()), row.sku(), row.productName(),
                    row.zone(), row.blockNo(), row.position(), utility, rowErrors.isEmpty(), List.copyOf(rowErrors)));
        }
        List<PlacementBlock> capacityBlocks = capacityBlocks(layout.role(), rows);
        errors.addAll(rules.capacityErrors(capacityBlocks));
        BigDecimal score = BigDecimal.ZERO;
        for (Integer product : distinctProducts) {
            BigDecimal value = utilities.get(product);
            if (value != null) score = score.add(value);
        }
        return new LayoutView(layout.id(), layout.campaignId(), layout.snapshotId(), layout.role(),
                layout.status(), layout.version(), layout.name(), score.setScale(6, RoundingMode.HALF_UP),
                errors.isEmpty(), List.copyOf(errors), List.copyOf(views), layout.createdAt(), layout.updatedAt());
    }

    private Map<Long, List<String>> recommendationErrors(Jwt jwt, LayoutRow layout, List<PlacementRow> rows) {
        var result = new HashMap<Long, List<String>>();
        var ids = new TreeSet<Long>();
        rows.stream().map(PlacementRow::recommendationId).filter(java.util.Objects::nonNull).forEach(ids::add);
        for (Long id : ids) {
            var errors = new ArrayList<String>();
            RecView current;
            try {
                current = recommendations.get(jwt, id);
            } catch (ApiException e) {
                if (e.status() != 404) throw e;
                result.put(id, List.of("Recommendation is no longer available"));
                continue;
            }
            List<PlacementRow> block = rows.stream().filter(row -> id.equals(row.recommendationId())).toList();
            if (current.snapshotId() != layout.snapshotId()) errors.add("Recommendation belongs to another snapshot");
            if (current.reviewStatus() != ReviewStatus.APPROVED) errors.add("Recommendation is not APPROVED");
            if (!current.readyForConsumption()) errors.add("Recommendation is not currently readyForConsumption");
            if (current.type() != RecType.valueOf(block.get(0).type())) errors.add("Recommendation type changed");
            List<Integer> storedProducts = block.stream().map(PlacementRow::productId).toList();
            if (!current.productIds().equals(storedProducts)) errors.add("Recommendation block no longer matches its saved products");
            String zone = rules.zone(current.type());
            if (block.stream().anyMatch(row -> !zone.equals(row.zone()))) errors.add("Recommendation is in the wrong layout zone");
            result.put(id, List.copyOf(errors));
        }
        return result;
    }

    private List<PlacementBlock> recommendationBlocks(Jwt jwt, int snapshotId, List<Long> recommendationIds) {
        if (recommendationIds == null) throw new ApiException(400, "recommendationIds is required");
        if (new HashSet<>(recommendationIds).size() != recommendationIds.size()) {
            throw new ApiException(400, "A recommendation may appear only once in a layout");
        }
        var result = new ArrayList<PlacementBlock>();
        var blockNumbers = new HashMap<String, Integer>();
        var positions = new HashMap<String, Integer>();
        for (Long recommendationId : recommendationIds) {
            RecView recommendation = requireConsumableRecommendation(jwt, recommendationId, snapshotId);
            String zone = rules.zone(recommendation.type());
            int blockNo = blockNumbers.merge(zone, 1, Integer::sum);
            int startPosition = positions.getOrDefault(zone, 0) + 1;
            positions.put(zone, startPosition + recommendation.productIds().size() - 1);
            result.add(new PlacementBlock(recommendationId, recommendation.type(), recommendation.productIds(),
                    zone, blockNo, startPosition));
        }
        return List.copyOf(result);
    }

    private RecView requireConsumableRecommendation(Jwt jwt, long recommendationId, int snapshotId) {
        RecView current = recommendations.get(jwt, recommendationId);
        if (current.snapshotId() != snapshotId) throw new ApiException(409, "Recommendation must use this campaign's snapshot");
        if (current.reviewStatus() != ReviewStatus.APPROVED) throw new ApiException(409, "Recommendation must be APPROVED");
        if (!current.readyForConsumption()) throw new ApiException(409, "Recommendation is not currently readyForConsumption");
        return current;
    }

    private void requireSelectedLayout(Jwt jwt, LayoutRow layout) {
        if (layout.status() != LayoutStatus.READY) throw new ApiException(409, "Selected layout is not READY");
        LayoutView current = layoutView(jwt, layout);
        if (!current.valid()) throw new ApiException(409, "Selected layout contains invalid blocks or exceeds configured capacity");
    }

    private void requireCurrentReport(int store, CampaignRow campaign, long reportId) {
        if (repository.totalReportsInCampaign(store, campaign.id(), reportId) != 1) {
            throw new ApiException(404, "Campaign report not found");
        }
        long versionA = repository.reportLayoutVersion(store, reportId, requireLayoutId(campaign.layoutAId()), LayoutRole.A);
        long versionB = repository.reportLayoutVersion(store, reportId, requireLayoutId(campaign.layoutBId()), LayoutRole.B);
        if (versionA != repository.layout(store, campaign.layoutAId(), false).version()
                || versionB != repository.layout(store, campaign.layoutBId(), false).version()) {
            throw new ApiException(409, "Simulation report does not match the current A/B layout versions; run a new simulation");
        }
    }

    private CampaignReportView reportView(int store, ReportRow row) {
        List<ReportLayoutView> layouts = repository.reportLayouts(store, row.reportId()).stream()
                .map(this::metricView).toList();
        Object assumptions;
        try {
            assumptions = json.readValue(row.assumptions(), Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored simulation assumptions are invalid JSON", e);
        }
        return new CampaignReportView(row.reportId(), row.campaignId(), row.snapshotId(),
                row.createdBy(), row.rangeStart(), row.rangeEnd(), row.sourceNamespace(), row.datasetHash(), row.evaluationCount(),
                row.simulated(), row.algorithmVersion(), assumptions, layouts, row.createdAt());
    }

    private ReportLayoutView metricView(ReportLayoutRow row) {
        return new ReportLayoutView(row.layoutId(), row.layoutVersion(), row.role(),
                new MetricView(row.hitCount(), row.hitDenominator(),
                        ratio(BigDecimal.valueOf(row.hitCount()), BigDecimal.valueOf(row.hitDenominator()))),
                new MetricView(row.bundleCount(), row.bundleDenominator(),
                        ratio(BigDecimal.valueOf(row.bundleCount()), BigDecimal.valueOf(row.bundleDenominator()))),
                new UtilityMetricView(row.utilityNumerator(), row.utilityDenominator(),
                        ratio(row.utilityNumerator(), row.utilityDenominator())), null, null);
    }

    private void saveInitialRevision(int store, long layoutId, int actor) {
        LayoutRow layout = repository.layout(store, layoutId, false);
        List<PlacementRow> placements = repository.placements(store, layoutId);
        repository.saveRevision(store, layoutId, layout.version(), placements, actor, hash(placements));
    }

    private CampaignView campaignView(Jwt jwt, CampaignRow row) {
        if (row.layoutAId() == null || row.layoutBId() == null) {
            throw new IllegalStateException("Campaign layout pair was not initialized");
        }
        return new CampaignView(row.id(), row.snapshotId(), row.name(), row.status(), row.version(),
                row.layoutAId(), row.layoutBId(), row.selectedLayoutId(), row.decision(),
                row.decisionReportId(), row.createdAt(), row.publishedAt(), row.activatedAt(), row.endedAt(),
                repository.campaignReportIds(row.storeId(), row.id()));
    }

    private static CampaignSummary summary(CampaignRow row) {
        return new CampaignSummary(row.id(), row.snapshotId(), row.name(), row.status(), row.version(),
                row.layoutAId(), row.layoutBId(), row.selectedLayoutId(), row.decision(), row.decisionReportId(),
                row.createdAt(), row.publishedAt(), row.activatedAt(), row.endedAt());
    }

    private static LayoutSummary summary(LayoutRow row) {
        return new LayoutSummary(row.id(), row.campaignId(), row.snapshotId(), row.role(), row.status(),
                row.version(), row.name(), row.createdAt(), row.updatedAt());
    }

    private List<PlacementBlock> capacityBlocks(LayoutRole role, List<PlacementRow> rows) {
        if (role == LayoutRole.A) {
            var blocks = new ArrayList<PlacementBlock>();
            int position = 1;
            for (PlacementRow row : rows) {
                blocks.add(new PlacementBlock(1L, RecType.BANNER, List.of(row.productId()),
                        "BASELINE", position, position));
                position++;
            }
            return blocks;
        }
        var grouped = new LinkedHashMap<Long, List<PlacementRow>>();
        for (PlacementRow row : rows) {
            if (row.recommendationId() != null) grouped.computeIfAbsent(row.recommendationId(), ignored -> new ArrayList<>()).add(row);
        }
        var blocks = new ArrayList<PlacementBlock>();
        for (Map.Entry<Long, List<PlacementRow>> entry : grouped.entrySet()) {
            List<PlacementRow> block = entry.getValue();
            if (block.get(0).type() == null) continue;
            RecType type = RecType.valueOf(block.get(0).type());
            blocks.add(new PlacementBlock(entry.getKey(), type, block.stream().map(PlacementRow::productId).toList(),
                    block.get(0).zone(), block.get(0).blockNo(), block.get(0).position()));
        }
        return blocks;
    }

    private static List<PlacementBlock> baselineBlocks(List<Integer> productIds) {
        var blocks = new ArrayList<PlacementBlock>();
        for (int i = 0; i < productIds.size(); i++) {
            int position = i + 1;
            blocks.add(new PlacementBlock(1L, RecType.BANNER, List.of(productIds.get(i)),
                    "BASELINE", position, position));
        }
        return blocks;
    }

    private static List<Set<Integer>> bundleBlocks(List<PlacementRow> rows) {
        var groups = new LinkedHashMap<Long, List<PlacementRow>>();
        for (PlacementRow row : rows) {
            if (row.recommendationId() != null && RecType.BUNDLE.name().equals(row.type())) {
                groups.computeIfAbsent(row.recommendationId(), ignored -> new ArrayList<>()).add(row);
            }
        }
        return groups.values().stream().map(rowsInBlock -> rowsInBlock.stream()
                .map(PlacementRow::productId).collect(java.util.stream.Collectors.toUnmodifiableSet())).toList();
    }

    private static Set<Integer> productSet(List<PlacementRow> rows) {
        var result = new TreeSet<Integer>();
        rows.forEach(row -> result.add(row.productId()));
        return Set.copyOf(result);
    }

    private static void requireUniqueProducts(List<PlacementBlock> blocks) {
        var products = new HashSet<Integer>();
        for (PlacementBlock block : blocks) {
            for (Integer product : block.productIds()) {
                if (!products.add(product)) throw new ApiException(400, "A product may appear only once in a layout");
            }
        }
    }

    private static void requireB(LayoutRow layout) {
        if (layout.role() != LayoutRole.B) throw new ApiException(409, "Layout A is immutable and read-only");
    }

    private static void requireDraft(CampaignRow campaign) {
        if (campaign.status() != CampaignStatus.DRAFT) throw new ApiException(409, "Layout changes require a DRAFT campaign");
    }

    private static void requireVersion(long current, Long expected, String message) {
        if (expected == null || expected != current) throw new ApiException(409, message);
    }

    private static long requireLayoutId(Long id) {
        if (id == null) throw new IllegalStateException("Campaign layout is missing");
        return id;
    }

    private static int actorId(Jwt jwt) {
        return Integer.parseInt(jwt.getSubject());
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) return null;
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize layout baseline parameters", e);
        }
    }

    private String hash(Object value) {
        try {
            byte[] bytes = json.writeValueAsBytes(value);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize immutable layout revision", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for layout revision provenance", e);
        }
    }

    private static void pagination(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > PAGE_MAX) {
            throw new ApiException(400, "page must be >=0 and pageSize in 1..100");
        }
    }
}
