# ROC-20261119-003 — F05 whole-đồng fallback for the SPMF input

| Field | Value |
|---|---|
| Record ID | ROC-20261119-003 |
| Date / timezone | 2026-11-19 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Opus 5.5) |
| Type | Implementation |
| Features | F05 mining input and views; F06 frozen-evidence reader |
| Branch / base HEAD | `Khoi/feat/f05-spmf-whole-dong-fallback`, created from `origin/dev` `530651bad5b02d90bcb48ecffaf7fa5a0f6b0b7e` |
| Execution status | COMPLETED for the scope below |
| Release status | PR_OPEN pending — branch pushed; the pull request is opened on GitHub (`gh` not available) |
| Daily summary | 2026-11-19 day summary is added on branch `Khoi/docs/decisions-20261119`; add this record to it after both are merged |

## 1. Request and scope

Owner decision of 2026-11-19 ("int32, option B"), recorded as [DEC-20261119-05](../../decisions/2026-11-19/DEC-20261119-05-spmf-whole-dong-fallback.md): realistic VND data must be mineable; utilities are rounded to 1 đồng HALF_UP only when building the SPMF input, and only when the exact input overflows an EFIM int32 bound. Frozen F03 snapshots stay exact. No migration, no change to historical rows.

## 2. Change details

### Added
- `MiningInputBuilder.prepare(snapshot, rows, mode, value)`: exact input and threshold first; on an EFIM int32 overflow (transaction utility, Product TWU, converted minUtil) a whole-unit input (`F03_WHOLE_UNIT_HALF_UP_INT32_DENSE_V1`, `utilityScale 0`, `divisor 1`, `conversionMode WHOLE_UNIT_HALF_UP`, `roundingUnit 1`). TU/TWU/total are sums of rounded items; minUtil is `ceil(value)` or `ceil(pct × roundedTotal / 100)`; a second overflow is 400 `… even after WHOLE_UNIT_HALF_UP rounding to 1 đồng`.
- `Dataset.mode()` / `MiningInputBuilder.modeFor(version)`: version dispatch. `conversionMode`/`roundingUnit` are serialized only for whole-unit inputs, so lossless frozen JSON keeps its previous shape.
- `RequestView`, `RunView`: additive `conversionMode`, `roundingUnit`.
- Opt-in `MiningConversionStabilityTest` (exact vs forced whole-đồng on an exported matrix) and a package-private builder hook used only by it.

### Changed
- Merchandiser, System Admin and scheduled request creation call `prepare` instead of `build` + `threshold`. `build()` and `threshold()` keep their exact behaviour and messages.
- `FrozenEvidenceReader` dispatches on the frozen version: lossless keeps the exact-total check; whole-unit checks the stored total against Σ HALF_UP(item) of the frozen matrix; other versions are rejected.
- `FoodMartScaleSyntheticE2EMySqlIntegrationTest` realistic-utility case now expects acceptance in whole-đồng mode and runs EFIM and F06 (its accepted branch used column names `runtime_millis`/`pattern_count`, which do not exist; now `runtime_ms`/`itemset_count`).

### Documentation
[DEC-20261119-05](../../decisions/2026-11-19/DEC-20261119-05-spmf-whole-dong-fallback.md); a "superseded in part" line appended to [DEC-20261117-01](../../decisions/2026-11-17/DEC-20261117-01-efim-aware-lossless-int32-guard.md); [F05 API](../../api/F05_MINING_API.md) (new section and view fields); [openapi.json](../../api/openapi.json) (two nullable properties on `RequestView` and `RunView`, from the runtime export); [F05 handoff](../../frontend_handoff/F05_HANDOFF.md); [F05 benchmark](../../testing/F05_FOODMART_SCALABILITY_BENCHMARK.md#whole-đồng-spmf-fallback--2026-11-19).

## 3. Files changed

| Path | Change |
|---|---|
| `src/main/java/vn/edu/fpt/paspm/mining/MiningInputBuilder.java` | Modified: parse/lossless/whole-unit split, `prepare`, version dispatch |
| `src/main/java/vn/edu/fpt/paspm/mining/MiningService.java`, `MiningScheduledRequest.java` | Modified: use `prepare` |
| `src/main/java/vn/edu/fpt/paspm/mining/MiningContracts.java` | Modified: additive view fields (old-arity constructors kept) |
| `src/main/java/vn/edu/fpt/paspm/recommendation/FrozenEvidenceReader.java` | Modified: version dispatch |
| `src/test/java/vn/edu/fpt/paspm/mining/MiningWholeUnitFallbackTest.java`, `MiningConversionStabilityTest.java` | Added |
| `src/test/java/vn/edu/fpt/paspm/mining/MiningIntegrationTest.java`, `SpmfMiningEngineTest.java`, `FoodMartScaleSyntheticE2EMySqlIntegrationTest.java`, `recommendation/FrozenEvidenceReaderTest.java` | Modified: new cases |
| `docs/…` | See §2 Documentation |

## 4. API and compatibility impact

Additive only: `RequestView.conversionMode`, `RequestView.roundingUnit`, `RunView.conversionMode`, `RunView.roundingUnit` (nullable). New 400 messages for a second overflow. Routes unchanged (100 paths / 121 operations). Historical requests keep `F03_SCALE6_GCD_INT32_DENSE_V1` and are read as before; their views report `LOSSLESS`. A request that was rejected before by an EFIM int32 bound may now be accepted in whole-đồng mode.

## 5. Verification executed in this session

Environment: Java 17.0.21 (Temurin) backend, SPMF 2.67a (`4aa46838…dfe6a`) on Java 25, disposable `mysql:8.4` container (8.4.13) on loopback, H2 for H2 suites.

| Command / method | Result |
|---|---|
| `./mvnw -o -B test -Dtest=MiningWholeUnitFallbackTest,MiningInputTest` (TDD: compile-red first) | 17 tests, 0 failures, 0 errors, 0 skipped |
| `./mvnw -o -B test -Dtest=MiningWholeUnitFallbackTest,MiningInputTest,SpmfMiningEngineTest,FrozenEvidenceReaderTest,MiningIntegrationTest` + SPMF/Java 25 properties | 79 tests, 0 failures, 0 errors, 0 skipped (after one assertion fix in the new reader test) |
| `./mvnw -o -B test -Dtest=FrozenEvidenceReaderTest,RecommendationRulesTest,VersionedUtilityPersistenceTest` | 28 tests, 0 failures, 0 errors, 0 skipped |
| `FoodMartScaleSyntheticE2EMySqlIntegrationTest#realisticUtilityDenseFixtureIsMinedInWholeDongModeThroughF06`, `-Dpaspm.test.foodmart-e2e=true -Dpaspm.test.mysql=true` (default `ABSOLUTE 500`; then `-Dpaspm.test.realistic-minutil=PERCENTAGE:1` and `PERCENTAGE:0.3`) | 1 test each, PASS (measurements in DEC-20261119-05) |
| `MiningConversionStabilityTest`, `-Dpaspm.test.stability-matrix=<exported demo matrix> -Dpaspm.test.stability-percentage=0.12` and `0.08` | PASS; 180/180 and 1,147/1,147 patterns shared, top-50 identical |
| Full suite: `./mvnw -o -B clean verify -DargLine=-Djava.io.tmpdir=… -Dpaspm.test.mysql=true -Dpaspm.test.java17-backend=true -Dpaspm.test.multijvm=true -Dpaspm.test.java17=… -Dpaspm.test.spmf-jar=… -Dpaspm.test.spmf-java=… -Dpaspm.avatar.directory=… -Dlogging.level.root=ERROR` | **694 tests, 0 failures, 9 errors, 12 skipped**. All 9 errors: `Cannot run program …/provision-restore.py: Permission denied` (the script was not executable in this worktree; CI runs `chmod +x` first) in `LegacyRecoveryMySqlTest` (7), `LegacyReaderMySqlTest` (1), `JpaPilotMigrationMySqlTest` (1). Skipped: opt-in classes `MiningConversionStabilityTest` (1), `FoodMartScaleSyntheticE2EMySqlIntegrationTest` (3), `MiningSchedulerUpgradeCompatibilityMySqlTest` (1), `MiningSchedulerMultiJvmMySqlTest` (2), `PilotCloneApiIntegrationTest` (5) |
| Rerun with an executable copy of the provisioner: `-Dtest=LegacyRecoveryMySqlTest,LegacyReaderMySqlTest,JpaPilotMigrationMySqlTest` (same opt-ins) | 17 tests, 0 failures, 0 errors, 0 skipped |
| Demo seed on a disposable stack (backend from this branch, Mailpit, `SEED_AS_OF=2026-11-19`), System Admin `PERCENTAGE` sweep on the 2026-10-01..04 snapshot | see DEC-20261119-05: 0.12 % → 180 patterns, LOSSLESS |
| GitHub CI | NOT RUN (runs when the PR is opened) |

## 6. Previous evidence referenced

ROC-20261117-015 (lossless guard; the realistic fixture's Product TWU 3,217,593,703). Record 014 (demo seed).

## 7. Limitations and next actions

- The realistic fixture uses prices 0.50–200.00, not VND; whole-đồng rounding is coarse for it (≈ 0.2 % of an average item). It is mineable now, but its minUtil must be chosen (`ABSOLUTE 500` exceeds the 2,000-pattern cap).
- If whole-đồng still overflows, the request is rejected; coarser units need a new owner decision.
- The forced whole-đồng switch exists only as a package-private builder method used by an opt-in test.
- `dev` moved to `d22f735` (F02) after this branch was cut; the PR shows whether a merge is clean.

## 8. Delivery

Commits on `Khoi/feat/f05-spmf-whole-dong-fallback`, pushed to `origin`; no merge into `dev` from this session.
