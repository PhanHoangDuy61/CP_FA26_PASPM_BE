# F02 Technical Decisions

## Approved implementation choices

- Follow the existing JDBC repository style in the Spring Boot modular monolith. The checked-in backend uses Spring JDBC rather than Spring Data JPA, so F02 follows the actual project architecture.
- Add Flyway `V3__f02_catalog_inventory_batch.sql`; keep `STORE` and applied V1/V2 migrations intact.
- Keep catalog functionality in `catalog.category`, `catalog.product`, and `catalog.inventory`. Controllers exchange records/DTOs and never expose database entities.
- Derive the current Store from the authenticated F01 user and `StoreScope`. No catalog write request accepts a Store ID. Every repository query constrains ownership by Store; product/category same-Store relationships are also database-enforced.
- Keep `PRODUCT` free of a stored stock field. Product response stock is derived from batch remaining quantities.
- Protect batch patches with a database row lock (`SELECT ... FOR UPDATE`) inside a transaction. Checks and updates therefore operate on the same locked state.
- Load development examples only through the `dev` profile seeder; it is idempotent and does not run under production profiles.

## Database constraints

- `CATEGORY`: Store foreign key, constrained status, and `(store_id, category_id)` key to support same-Store product relationships.
- `PRODUCT`: Store foreign key; unique SKU per Store; same-Store category composite foreign key; nonnegative `DECIMAL(12,2)` prices; constrained `ACTIVE`/`INACTIVE` status.
- `INVENTORY_BATCH`: product foreign key; unique batch code per product; positive initial quantity; remaining quantity from zero through initial quantity; constrained `ACTIVE`/`BLOCKED` status.
- Query indexes cover Store/category/status, Store/product name, and product/batch status/expiry filters.
- Expiry is optional at schema level because only products configured with `requires_expiry=true` require it; the API enforces this product rule.

## Inventory summary rules

- Use the user's approved MVP timezone `Asia/Ho_Chi_Minh` as the default `paspm.store-time-zone`; deployments may override the property until Store records gain a timezone field.
- Expiry dates are inclusive through the current local calendar date. A batch expires at the start of the next local date.
- `totalQuantity` includes all remaining units. `BLOCKED` units count only toward `blockedQuantity`, even when their expiry date is past. Active expired units count toward `expiredQuantity`. Active non-expired units count toward `sellableQuantity`.
- `expiringSoonQuantity` follows F02-08 issue wording: it is a subset of sellable quantity with expiry date from today through today plus `expiringWithinDays`, inclusive (default 7).
- These buckets do not double-count. Empty batches return zero for every quantity.

## F01 authorization integration

- F02 controller routes require an authenticated `STORE_ADMIN`; the existing JWT, role checks, `AuthService`, `StoreScope`, and `{ "error": "..." }` response style remain in use.
- Unauthenticated requests return `401`, other authenticated roles return `403`, and resources outside the current Store return `404`.
- Client-supplied `storeId` is rejected as an unknown request property and cannot change the authenticated Store scope.

## Scope boundaries and open decisions

- No stock movements, ingredient/recipe inventory, Custom Weight, Expiry Weight, or Seasonality Weight are introduced.
- F02-08 calls out `expiringSoonQuantity` despite the shorter common summary field list; the issue document is retained as the approved detailed contract.
- Store-specific timezone persistence is not in the current ERD; use the user-approved MVP timezone until a later schema requirement is approved.
