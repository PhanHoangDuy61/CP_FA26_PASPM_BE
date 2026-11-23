# DEC-20261123-01 — Store management (F01): Store profile, status, branding images

| Field | Value |
|---|---|
| Date | 2026-11-23 (Asia/Ho_Chi_Minh) |
| Source | Product Owner answers at Checkpoint 1 and Checkpoint 2 of the Store-management task (2026-11-23), and the PO's branding-image requirement of the same day |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/store-management`; PR to `dev` pending owner approval |
| Feature | F01 (Identity & access). New use cases UC34 Manage Stores (System Administrator) and UC35 Manage own Store profile (Store Admin; Merchandiser view only) |
| Related | DEC-20261117-03 (F09/F10 MVP), DEC-20261119-01 (PR-only `dev`), PO register D2 (Store picker) and D4 (one Store calendar zone) |

## Context
Before this decision no requirement source defined Store management:
- the approved brief, Reports 1–4, `docs/product/*` and the earlier DEC files say nothing about it;
- the only Store API was the System Admin picker `GET /api/system/stores` (`{storeId, name}`);
- Store users could not see their own Store;
- the `store` table had not changed since V1 (`store_id`, `name`, `utility_config`, `mining_schedule`).

## Decision

### 1. Store data (migration V27; V1–V26 untouched)
- `name` is required, at most 150 characters, and stored trimmed.
  - It is **unique after trimming, ignoring case**. The service checks this on create and rename.
  - V27 adds no database UNIQUE constraint on `name`. Duplicate Store names in the dev/demo seeds and tests are renamed, so a UNIQUE constraint can be added later.
- `slug` is the F09 public `storeSlug`.
  - Format: lowercase letters, digits and single hyphens; 1–50 characters.
  - It is unique (database UNIQUE plus a service check).
  - It is set at creation and **editable only by SYSTEM_ADMIN**.
  - V27 backfills every existing row with `slugify(name)-{store_id}` *before* it adds NOT NULL and UNIQUE. Because the id suffix is unique, duplicate names cannot produce a duplicate slug.
  - **Every Store created through the API gets a real slug**: `POST /api/system/stores` requires one, and only a System Admin can change it with `PUT`. The random-UUID default on `store.slug` exists **only** so that raw-SQL test fixtures and older scripts that omit the column keep working. No application path relies on it.
- `status` is `ACTIVE` or `INACTIVE` (default `ACTIVE`, with a CHECK constraint).
- `version` is used for optimistic locking. Writes send `expectedVersion`; a stale version returns 409. Every change to the row increments it, image changes included.
- `created_at` and `updated_at`.
- `address` and `phone` are optional. There is no e-mail field.
- `deactivated_at` and `deactivated_by` (an FK to `app_user`) are both nullable. Deactivation sets them and reactivation clears them.
  - The backend has no general audit-log table. Only domain histories exist (`campaign_history`, `recommendation_review_history`, `mining_config_version`), so these columns are kept.
- There is no per-Store time zone. The system zone stays `Asia/Ho_Chi_Minh` (D4).
- `utility_config` is unchanged; it holds live Weight v2 data. `mining_schedule` is not touched; removing it is a separate cleanup task.

### 2. System Administrator (UC34)
- A System Admin can create a Store, update every field (including `slug`), and deactivate or reactivate it through one `PATCH …/status` route.
- **There is no hard delete**, because 14 tables reference `store`.
- The picker `GET /api/system/stores` is kept and changed only additively:
  - it returns new fields;
  - it accepts the new filters `q` and `status`;
  - **by default it lists only ACTIVE Stores**. `status=INACTIVE` or `status=ALL` must be asked for.
- The Store detail shows the **number of assigned users** and lists them read-only. Assigning users stays in F01 account management.

### 3. Store Admin and Merchandiser (UC35)
- A STORE_ADMIN views their own Store. They may edit only `address` and `phone`, not `name`, `slug` or `status`.
- A MERCHANDISER may view their own Store only.
- The Store is always taken from the authenticated account, never from the request.

### 4. Deactivation
- **One central guard** rejects users of an INACTIVE Store with **HTTP 403** and `{"error": "Store is inactive", "code": "STORE_INACTIVE"}`.
  - It runs where the request loads the user's row (and with it the Store) from the database: the JWT authentication converter in `SecurityConfig`.
  - The same check runs at token issuance: login, Google login and token refresh.
  - No per-service checks are added.
- Paths the central guard cannot cover, because no Store user is involved, each get an explicit check:

  | Path | Check |
  |---|---|
  | System Admin creates new work for an INACTIVE Store: a manual mining request, creating a schedule, or enabling a schedule | **409 `STORE_INACTIVE`** — reactivate first. Disabling a schedule, and replaying an existing mining request key, stay allowed |
  | Imports | Only STORE_ADMIN can import, and the central guard already covers them |
  | Assigning a user to a Store (`POST /api/users`, `PUT /api/users/{id}/role`) | **409 `STORE_INACTIVE`** for an INACTIVE Store |
  | Scheduled mining reservation | A due fire time of an INACTIVE Store is recorded as a SKIPPED occurrence with reason `STORE_INACTIVE`, and the schedule cursor moves on. Occurrences already reserved, and queued or running mining requests and imports, are in-flight work and finish |
  | Public image GET | Images are served only for ACTIVE Stores (§5) |

- Historical data stays readable.

### 5. Store branding images (icon, banner)
- V27 adds nullable `store.icon_key` and `store.banner_key` (`VARCHAR(512)`).
  - They hold a **relative storage key** `stores/{storeId}/{uuid}.{png|jpg}`, never a BLOB or an absolute URL.
  - The API builds the URL.
- **Storage:** a shared `MediaStorage` interface in `vn.edu.fpt.paspm.common.media`, implemented on the local filesystem by `LocalMediaStorage`.
  - The root comes from `PASPM_MEDIA_ROOT` (property `paspm.media.root`). The default is `~/.paspm/media`.
  - The Docker image uses `/var/lib/paspm/media`, on the named volume `paspm_media`.
  - Avatars use the same `LocalMediaStorage` implementation with their own unchanged directory. `paspm.avatar.directory` (`PROFILE_IMAGE_DIRECTORY`) still defaults to `~/.paspm/profile-images`. In Docker both are on the `paspm_media` volume: `PASPM_MEDIA_ROOT=/var/lib/paspm/media` and `PROFILE_IMAGE_DIRECTORY=/var/lib/paspm/media/avatars`.
  - A database key whose file is missing answers **404**, never 503. This applies to Store images and avatars.
- **Types:** PNG and JPEG only (no WebP in the MVP, no SVG). Each file is checked by its magic bytes and then decoded with ImageIO, which also gives the dimensions.
- **Limits:** the icon is at most 1 MiB, the banner at most 5 MiB, and both at most 4000×4000 px. The stored file name is a random UUID, never the client's file name.
- **Replacing an image:** the old file is deleted **after commit**; a failed delete only logs a warning. On rollback, the newly written file is deleted.
- **Permissions:**
  - SYSTEM_ADMIN may upload and delete images for any Store.
  - STORE_ADMIN may do so for their own Store only. Another Store returns 404.
  - MERCHANDISER may only read.
- **Serving:**
  - `GET /api/public/stores/{storeId}/images/{fileName}` is public. It serves only the **current** icon or banner, only for an **ACTIVE** Store. It sends long cache headers (`public, max-age=31536000, immutable`) and `X-Content-Type-Options: nosniff`. Any other request returns 404.
  - `GET /api/stores/{storeId}/images/{kind}` is authenticated. A SYSTEM_ADMIN can use it for any Store, including an INACTIVE one; Store users can use it for their own Store.
  - When there is no image, the URL is `null` and the frontend shows a placeholder.
- **Scope:** this feature covers the columns, the upload, delete and read API, and the upload UI with preview. Rendering on the storefront belongs to F09.

### 6. API (final shape)
- System Admin:
  - `GET` and `POST /api/system/stores`;
  - `GET` and `PUT /api/system/stores/{storeId}`;
  - `PATCH /api/system/stores/{storeId}/status`.
- Own Store: `GET` and `PUT /api/store/profile`.
- Images:
  - `GET`, `PUT` and `DELETE /api/stores/{storeId}/images/{kind}`, where `kind` is `icon` or `banner`;
  - public `GET /api/public/stores/{storeId}/images/{fileName}`.
- That makes 5 new paths and 10 new operations. The real counts come from the runtime OpenAPI export.

### 7. Not part of this feature
- Fixing the product docs that the server-generated SKU (`24b9b01`) made outdated is a separate task.
- Dropping `store.mining_schedule` is a separate cleanup task.
- The local avatar folder does not move (Checkpoint 3), so no avatar migration is needed.

## Consequences
- **API:**
  - Response fields are only added. There are new System Admin, own-Store, image and public routes.
  - The picker default changes to ACTIVE only, so its two frontend callers (mining and account creation) no longer offer an INACTIVE Store.
- **Security:**
  - The first anonymous route is the public image GET (GET only, ACTIVE Stores, current files only).
  - Users of an INACTIVE Store get 403 `STORE_INACTIVE` on every authenticated request and at login or refresh.
- **Local data:**
  - Local avatars stay in `~/.paspm/profile-images`, so nothing moves.
  - The Docker image never had a volume for avatars, so container avatars were lost on every recreate. Avatars and Store images now both live on the `paspm_media` volume.
- **Docs:** `docs/product/*`, the F01 API doc, the RBAC matrix, OpenAPI, the frontend handoff, the setup docs (media root and Docker volume), the ERD (`store` table) and the UML pack (UC34/UC35, FR, Screen Flow, AD09, R2-06.07, R2-08.09, R2-07.09, `common.media` in R2-05).
