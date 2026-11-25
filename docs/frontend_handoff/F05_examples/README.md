# F05 executed JSON examples

These JSON files were captured from actual Swagger UI requests/responses against the isolated native MySQL QA backend on2026-10-19. They contain synthetic Store/Product/user IDs, no credentials or tokens. IDs refer to that test database and must be replaced when integrating another Store/environment. The create requestKey is an example; generate a fresh UUID per deliberate submission and retain it for transport retry.

- create_request.json / create_response.json: POST202, captured PENDING.
- request_response.json: same persisted request later COMPLETED/MATCH.
- snapshot_list_response.json: Store-scoped READY snapshot picker.
- request_list_response.json: paginated history.
- runs_response.json / run_response.json: two real algorithm runs and individual metrics.
- patterns_response.json: eight normalized HUI-Miner patterns (the EFIM set/utilities matched exactly).
- benchmark_response.json: actual runtime/heap/methodology and comparison status.
- percentage_request.json / percentage_response.json:31.25% of converted96 -> threshold30.
- error_conflict_response.json:409 on changed payload with same requestKey.
- error_validation_response.json:400 percentage101.
- error_cross_store_response.json:404 on another Store's run patterns.
- error_forbidden_response.json:403 for a forbidden role.

Actual UI evidence and complete23-case request/response inventory are in `../../PASPM_F05_High_Utility_Mining/evidence/swagger-ui-results.json`. Full field/type rules are in `../../api/F05_MINING_API.md`; UI integration order and limitations are in `../F05_HANDOFF.md`. Empty401 authentication bodies are valid and not represented as invented JSON examples.
