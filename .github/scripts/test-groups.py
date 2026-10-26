"""Select only existing suites; report missing JPA coverage explicitly."""
from pathlib import Path
import sys

GROUPS = {
    "backend": ["AuthIntegrationTest", "GoogleRegistrationTest", "CatalogDevSeederTest", "CatalogIntegrationTest",
                "LegacyPreflightTest", "MiningInputTest", "FrozenEvidenceReaderTest", "RecommendationRulesTest",
                "TransactionEvidenceTest", "DatabaseTransactionSourceTest", "TransactionImportIntegrationTest",
                "TransactionImportSchemaTest", "TransactionCsvParserTest", "TransactionOrderValidatorTest",
                "TransactionImportCorrectnessIntegrationTest", "TransactionOrderIdentifierTest",
                "TransactionExcelParserTest", "TransactionExcelImportIntegrationTest", "TransactionMoneyBoundsTest",
                "ExpiryBinaryRulesTest", "RetailCatalogUtilityIntegrationTest", "UtilityCalculationTest",
                "UtilityFixtureTest", "UtilityIntegrationTest", "UtilitySnapshotRoutingTest",
                "WeightRulesV12IntegrationTest", "WeightRulesV12MigrationTest"],
    "mysql": ["FlywayRecoveryTest", "LegacyRecoveryMySqlTest", "LegacyRecoverySchemaTest",
              "RecommendationMigrationTest", "TransactionImportMySqlIntegrationTest",
              "TransactionImportCorrectnessMySqlIntegrationTest", "TransactionRawEvidenceMigrationMySqlTest",
              "TransactionExcelImportMySqlIntegrationTest",
              "RetailCatalogUtilityMySqlIntegrationTest"],
}

if __name__ == "__main__":
    selected = GROUPS[sys.argv[1]]
    available = {path.stem for path in Path("src/test/java").rglob("*Test.java")}
    if missing := set(selected) - available:
        raise SystemExit("Expected existing test suites missing: " + ", ".join(sorted(missing)))
    print(",".join(selected))
