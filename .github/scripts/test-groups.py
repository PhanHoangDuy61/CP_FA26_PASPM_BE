"""Select only existing suites; report missing JPA coverage explicitly."""
from pathlib import Path
import sys

GROUPS = {
    "backend": ["AuthIntegrationTest", "GoogleRegistrationTest", "SessionHttpIntegrationTest", "SessionOriginGuardTest", "SessionV21MigrationTest", "CatalogDevSeederTest", "CatalogIntegrationTest", "StorefrontIntegrationTest",
                "LegacyPreflightTest", "MiningInputTest", "FrozenEvidenceReaderTest", "RecommendationRulesTest",
                "TransactionEvidenceTest", "DatabaseTransactionSourceTest", "TransactionImportIntegrationTest",
                "TransactionImportSchemaTest", "TransactionCsvParserTest", "TransactionOrderValidatorTest",
                "TransactionImportCorrectnessIntegrationTest", "TransactionOrderIdentifierTest",
                "TransactionExcelParserTest", "TransactionExcelImportIntegrationTest", "TransactionMoneyBoundsTest",
                "ExpiryBinaryRulesTest", "RetailCatalogUtilityIntegrationTest", "UtilityCalculationTest",
                "UtilityFixtureTest", "UtilityIntegrationTest", "UtilitySnapshotRoutingTest", "MigrationVersioningTest", "MiningOccurrenceDispatcherFlagTest",
                "F03F05RbacMatrixTest", "ScheduledMiningDisabledByDefaultTest", "SystemStoreReadPolicyTest",
                "MiningSystemStoreReadGateTest", "MiningSystemStoreReadGranularGateTest", "MiningAdminConfigurationTest",
                "MiningScheduleTimeEdgeCasesTest", "MiningScheduleReservationScenariosTest", "MiningOccurrenceFailurePathsTest", "MiningScheduleAdministrationHttpTest",
                "ScheduledMiningZoneGuardTest", "SystemStoreListHttpTest", "MiningScheduleCatchUpTest", "MiningScheduleChangeSemanticsTest",
                "SchedulerPrincipalHttpTest", "ScheduledMiningActivationGateTest", "MiningScheduleReservationTest",
                "MiningOccurrenceLeaseTest", "MiningOccurrenceReconciliationTest",
                "PushDayIntegrationTest", "WeightV2CalculationTest", "VersionedUtilityJsonTest", "VersionedUtilityPersistenceTest", "WeightRulesV12MigrationTest",
                "StoreMigrationV27Test", "StoreInactiveGuardHttpTest", "StoreAdministrationHttpTest", "StoreProfileHttpTest",
                "StoreImageHttpTest", "LocalMediaStorageTest", "MediaFileMissingHttpTest",
                "LayoutCampaignIntegrationTest",
                "BatchExpiryRulesTest", "ExpiryBoundaryF03F06Test", "ProductSoftDeleteIntegrationTest", "ProductSoftDeleteV28MigrationTest",
                "StorefrontIntegrationTest", "SpmfProcessBoundaryTest", "SpmfThirdPartyNoticesTest"],
    "mysql": ["SessionMySqlIntegrationTest", "SessionV21MigrationMySqlTest", "FlywayRecoveryTest", "LegacyRecoveryMySqlTest", "LegacyRecoverySchemaTest",
              "RecommendationMigrationTest", "TransactionImportMySqlIntegrationTest",
              "TransactionImportCorrectnessMySqlIntegrationTest", "TransactionRawEvidenceMigrationMySqlTest",
              "TransactionExcelImportMySqlIntegrationTest",
              "RetailCatalogUtilityMySqlIntegrationTest", "MiningMigrationMySqlTest", "StoreMigrationV27MySqlTest",
              "ProductSoftDeleteV28MySqlTest", "JpaPilotMigrationMySqlTest"],
}

if __name__ == "__main__":
    selected = GROUPS[sys.argv[1]]
    available = {path.stem for path in Path("src/test/java").rglob("*Test.java")}
    if missing := set(selected) - available:
        raise SystemExit("Expected existing test suites missing: " + ", ".join(sorted(missing)))
    print(",".join(selected))
