# F03 v1.2 examples

Current Preview, threshold3/0 Preview, Snapshot and Matrix response JSON files are actual synthetic outputs exported by WeightRulesV12IntegrationTest on 2026-10-19. They contain no credentials. Tests use isolated Stores and generated identities; do not expect IDs/hashes to match production or different test scenarios. Snapshot and matrix examples are a matched frozen pair. Preview is a separate scenario.

The official fixture uses fixed analysisDate2026-10-19, historical Base12000, S.8/M.9, coefficients.5/.2/.3 and lambda.5. Product T7 yields E1/W1.435/17220; T3/0 yields E0/W1.335/16020. Current source is F04_DATABASE_V1. Product threshold request is the full F02 Product POST/PUT, shown in ../F02_examples.

utility_config_update_request.json, lambda policy and manual score requests retain their implemented DTOs. expiry_window_update_request.json is a **deprecated legacy request**; it has no scoring effect. utility_config_response.json illustrates a new current config with no legacy Store threshold.

legacy_linear_snapshot_response.json and legacy_linear_matrix_response.json preserve the older synthetic F03_LINEAR_V1 response shapes/decimal values solely as historical-read examples. They are not current calculation fixtures and must not drive new E calculations. New fields such as expiryEvidence/timeZone may be absent in these older shapes; current reads expose null when storage lacks them.

See ../F03_HANDOFF.md and ../../api/F03_UTILITY_API.md for full current contracts and nullability.

## Current Product read model — 2026-10-21

`current_product_scores_e1_response.json`, `current_product_scores_e0_response.json` and `current_product_scores_stale_lambda_response.json` are actual standalone MySQL GET/PATCH results from the Backend Catalog integration verification. They demonstrate E1/W1.435, E0/W1.335 and an out-of-policy lambda (Wnull/readyfalse), using current Store-local dates and no transaction requirement. They are not frozen snapshot responses. See ../../requirements/RETAIL_CATALOG_UTILITY_VERIFICATION.md for separate HTTP and interactive Swagger UI evidence. No credentials are included.
