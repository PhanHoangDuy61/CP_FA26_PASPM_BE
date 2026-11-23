# F01 — Store management API (UC34, UC35)

Rules: [DEC-20261123-01](../decisions/2026-11-23/DEC-20261123-01-store-management.md). Branch `Khoi/feat/store-management` (base `dev` `6d2a877`), PR to `dev` pending. Migration **V27**.

## Conventions
- Failures use `{"error": "…"}`. Machine-handled cases add `code`; this feature uses `STORE_INACTIVE`.
- Request bodies are strict: an unknown field returns **400** `Invalid request fields`.
- Versioned writes send `expectedVersion`. A stale version returns **409** `Store version conflict`. Every change to the Store, image changes included, increments `version`. After an image upload or removal, clients must re-fetch the Store and use the new `version` before `PUT /api/store/profile` or `PUT /api/system/stores/{storeId}`.
- Order of checks: 401 (no or invalid token) → 403 (role) → 404 (unknown Store, or another Store for a Store user).

## Views

`StoreView`:

| Field | Type | Notes |
|---|---|---|
| `storeId` | int | |
| `name` | string | Trimmed. Unique ignoring case (service rule) |
| `slug` | string | `[a-z0-9]+(-[a-z0-9]+)*`, at most 50 characters, unique. The F09 public `storeSlug` |
| `status` | `ACTIVE` \| `INACTIVE` | |
| `address`, `phone` | string \| null | |
| `iconUrl`, `bannerUrl` | string \| null | Public URL `/api/public/stores/{storeId}/images/{uuid}.{png\|jpg}`. It serves only while the Store is ACTIVE. `null` means no image, so the frontend shows a placeholder |
| `version` | long | |
| `createdAt`, `updatedAt`, `deactivatedAt` | ISO-8601 instant \| null | |

`StoreDetailView` (System Admin) is `StoreView` plus:
- `deactivatedBy` (user id or null);
- `assignedUserCount`;
- `assignedUsers` — a read-only list of `{userId, name, email, role, status}`. Assigning users stays in F01 account management.

## Routes

| Method and path | Role | Request | Response | Errors |
|---|---|---|---|---|
| `GET /api/system/stores?page&pageSize&q&status` | SYSTEM_ADMIN | `page` ≥ 0 (default 0), `pageSize` 1..100 (default 20). `status` = `ACTIVE` (default), `INACTIVE` or `ALL`. `q` matches name or slug, ignoring case | `Page<StoreView>` ordered by name, then `storeId` | 400 bad pagination or status |
| `POST /api/system/stores` | SYSTEM_ADMIN | `{name, slug, address?, phone?}` | **201** `StoreDetailView` (ACTIVE, version 1) | 400 validation; 409 `Store name already exists` / `Store slug already exists` |
| `GET /api/system/stores/{storeId}` | SYSTEM_ADMIN | — | `StoreDetailView` | 404 |
| `PUT /api/system/stores/{storeId}` | SYSTEM_ADMIN | `{expectedVersion, name, slug, address, phone}` (address and phone: null or blank clears them) | `StoreDetailView` | 400 (including `status`); 404; 409 stale or duplicate |
| `PATCH /api/system/stores/{storeId}/status` | SYSTEM_ADMIN | `{expectedVersion, status: ACTIVE\|INACTIVE}` | `StoreDetailView`. INACTIVE sets `deactivatedAt`/`deactivatedBy`; ACTIVE clears them | 400; 404; 409 stale, or `Store is already ACTIVE/INACTIVE` |
| `GET /api/store/profile` | STORE_ADMIN, MERCHANDISER | — | `StoreView` of the account's own Store | 403 other roles |
| `PUT /api/store/profile` | STORE_ADMIN | `{expectedVersion, address, phone}` only | `StoreView` | 400 (`name`/`slug`/`status` are unknown fields); 403; 409 stale |
| `GET /api/stores/{storeId}/images/{kind}` | SYSTEM_ADMIN (any Store, INACTIVE included); STORE_ADMIN and MERCHANDISER (own Store) | `kind` = `icon` or `banner` | Image bytes with `Cache-Control: private, no-store` and `X-Content-Type-Options: nosniff` | 403; 404 no image, unknown kind, or another Store |
| `PUT /api/stores/{storeId}/images/{kind}` | SYSTEM_ADMIN (any); STORE_ADMIN (own) | multipart, part `file` | `StoreView` | 400 type, content or dimensions; **413** icon > 1 MiB or banner > 5 MiB; 403 Merchandiser; 404 |
| `DELETE /api/stores/{storeId}/images/{kind}` | as PUT | — | `StoreView` (URL `null`) | 403; 404 |
| `GET /api/public/stores/{storeId}/images/{fileName}` | **public** (no token) | — | The current icon or banner, with `Cache-Control: public, max-age=31536000, immutable` and `nosniff` | 404 unknown or INACTIVE Store, or a file name that is not the current icon or banner |

### Field rules
- `name`: required, at most 150 characters after trimming, not blank.
- `slug`: required on create and on PUT. Only a System Admin can change it.
- `address`: at most 255 characters.
- `phone`: at most 30 characters, using digits, spaces and `+ ( ) . -`.

### Image rules
- Only PNG or JPEG are accepted. The type is checked from the file's magic bytes, then the file is decoded with ImageIO. WebP, SVG, GIF and corrupt files return 400.
- Images may be at most 4000×4000 px.
- The file is stored as `stores/{storeId}/{uuid}.{png|jpg}` under `PASPM_MEDIA_ROOT`. The client file name is ignored.
- When an image is replaced or removed, the old file is deleted after commit; a failed delete only logs a warning. If the request rolls back, the new file is deleted.

## INACTIVE Store (DEC-20261123-01 §4)
- **Central guard.** Every authenticated request by a user of an INACTIVE Store returns **403** `{"error":"Store is inactive","code":"STORE_INACTIVE"}`. Login and token refresh return the same response.
  - A wrong password still returns 401.
  - The refresh session is kept, so it works again after reactivation.
- **New work by a System Admin.** A manual mining request, creating a schedule or enabling a schedule for an INACTIVE Store returns **409** `code: STORE_INACTIVE`. Replaying an existing request key and disabling a schedule are still allowed.
- **User assignment.** Assigning a user to an INACTIVE Store (`POST /api/users`, `PUT /api/users/{id}/role`) returns **409** `code: STORE_INACTIVE`.
- **Scheduler.** Due fire times of an INACTIVE Store are recorded as `SKIPPED` occurrences with `skipReason: STORE_INACTIVE`. Occurrences already reserved still run.
- **History.** History reads stay available.

## Tests
- `StoreMigrationV27Test` (H2) and `StoreMigrationV27MySqlTest` (MySQL 8.4).
- `StoreInactiveGuardHttpTest`.
- `StoreAdministrationHttpTest`, `StoreProfileHttpTest`.
- `StoreImageHttpTest`, `LocalMediaStorageTest`.
- `SystemStoreListHttpTest` (updated picker).
