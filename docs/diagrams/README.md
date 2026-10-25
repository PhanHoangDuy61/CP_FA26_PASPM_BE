# Diagrams and Database Models

| Model | Status | Scope |
|---|---|---|
| [F03-named editable model](PASPM_Complete_ERD_F03_Three_Weights_v4.drawio) | NEEDS_REVIEW | Actual pages include final weights v1.2/F05/F06 V10 despite filename; V11 archive/routing is missing |
| [Original F02 v3 model](PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio) | SUPERSEDED for later schema | Original iteration reference, retained unchanged |

The [second F02 copy](../features/F02/reference_v3/PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio) has identical bytes and separate original-backlog provenance; it is intentionally preserved. No diagram contents or hashes are changed.

Physical schema authority: [Flyway SQL](../../src/main/resources/db/migration), [Java V11](../../src/main/java/db/migration/V11__historical_utility_matrix_storage.java), [legacy compatibility](../architecture/LEGACY_UTILITY_MATRIX_COMPATIBILITY.md). [Path map](../DOCUMENTATION_MAP.md) resolves old `ERD_Diagram` locations; old names/recorded manifests are not evidence of a newer physical model.
