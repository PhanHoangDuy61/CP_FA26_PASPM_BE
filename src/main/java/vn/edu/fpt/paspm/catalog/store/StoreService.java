package vn.edu.fpt.paspm.catalog.store;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.fpt.paspm.catalog.store.StoreContracts.ChangeStatus;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.CreateStore;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.StoreDetailView;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.StoreView;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.UpdateProfile;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.UpdateStore;
import vn.edu.fpt.paspm.catalog.store.StoreRepository.StoreRow;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.MiningContracts;

/**
 * DEC-20261123-01 §1–§3. Store names are unique after trimming, ignoring case (a service rule: there is no UNIQUE on
 * {@code name}); slugs are unique (database UNIQUE). Every write checks {@code expectedVersion}. Stores are never deleted.
 */
@Service
public class StoreService {
    private static final int MAX_PAGE_SIZE = 100;
    private final StoreRepository stores;

    public StoreService(StoreRepository stores) {
        this.stores = stores;
    }

    @Transactional(readOnly = true)
    public MiningContracts.Page<StoreView> list(String q, String status, int page, int pageSize) {
        if (page < 0 || page > 100_000 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(400, "Invalid pagination");
        }
        String filter = switch (status == null ? "ACTIVE" : status) {
            case "ACTIVE", "INACTIVE" -> status == null ? "ACTIVE" : status;
            case "ALL" -> null;
            default -> throw new ApiException(400, "Invalid status filter");
        };
        String query = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        if (query != null && query.length() > 150) {
            throw new ApiException(400, "Invalid search query");
        }
        var items = stores.findPage(query, filter, page, pageSize).stream().map(StoreService::view).toList();
        return new MiningContracts.Page<>(items, page, pageSize, stores.count(query, filter));
    }

    @Transactional
    public StoreDetailView create(CreateStore request) {
        String name = name(request.name());
        var all = stores.lockAll();
        requireUniqueName(all, name, 0);
        if (stores.slugTaken(request.slug(), 0)) {
            throw new ApiException(409, "Store slug already exists");
        }
        int id = stores.insert(name, request.slug(), optional(request.address()), optional(request.phone()));
        return detail(id);
    }

    @Transactional(readOnly = true)
    public StoreDetailView detail(int storeId) {
        StoreRow row = stores.find(storeId).orElseThrow(StoreService::notFound);
        var users = stores.assignedUsers(storeId);
        return new StoreDetailView(row.storeId(), row.name(), row.slug(), row.status(), row.address(), row.phone(),
                imageUrl(row, row.iconKey()), imageUrl(row, row.bannerKey()), row.version(), row.createdAt(), row.updatedAt(),
                row.deactivatedAt(), row.deactivatedBy(), users.size(), users);
    }

    @Transactional
    public StoreDetailView update(int storeId, UpdateStore request) {
        String name = name(request.name());
        StoreRow row = stores.lock(storeId).orElseThrow(StoreService::notFound);
        requireVersion(row, request.expectedVersion());
        requireUniqueName(stores.lockAll(), name, storeId);
        if (stores.slugTaken(request.slug(), storeId)) {
            throw new ApiException(409, "Store slug already exists");
        }
        if (stores.update(storeId, request.expectedVersion(), name, request.slug(), optional(request.address()), optional(request.phone())) != 1) {
            throw versionConflict();
        }
        return detail(storeId);
    }

    /** One route for deactivate and reactivate; INACTIVE records who and when, ACTIVE clears it. */
    @Transactional
    public StoreDetailView changeStatus(int storeId, ChangeStatus request, int actorId) {
        StoreRow row = stores.lock(storeId).orElseThrow(StoreService::notFound);
        requireVersion(row, request.expectedVersion());
        if (row.status().equals(request.status())) {
            throw new ApiException(409, "Store is already " + row.status());
        }
        if (stores.updateStatus(storeId, request.expectedVersion(), request.status(), actorId) != 1) {
            throw versionConflict();
        }
        return detail(storeId);
    }

    @Transactional(readOnly = true)
    public StoreView profile(int storeId) {
        return view(stores.find(storeId).orElseThrow(StoreService::notFound));
    }

    @Transactional
    public StoreView updateProfile(int storeId, UpdateProfile request) {
        StoreRow row = stores.lock(storeId).orElseThrow(StoreService::notFound);
        requireVersion(row, request.expectedVersion());
        if (stores.updateContact(storeId, request.expectedVersion(), optional(request.address()), optional(request.phone())) != 1) {
            throw versionConflict();
        }
        return profile(storeId);
    }

    static StoreView view(StoreRow row) {
        return new StoreView(row.storeId(), row.name(), row.slug(), row.status(), row.address(), row.phone(),
                imageUrl(row, row.iconKey()), imageUrl(row, row.bannerKey()), row.version(), row.createdAt(), row.updatedAt(),
                row.deactivatedAt());
    }

    /** The public URL of the current file (see {@link StoreImageUrls}, shared with the F09 storefront). */
    static String imageUrl(StoreRow row, String key) {
        return StoreImageUrls.publicUrl(row.storeId(), key);
    }

    private static void requireUniqueName(java.util.List<StoreRow> all, String name, int excludeStoreId) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (StoreRow row : all) {
            if (row.storeId() != excludeStoreId && row.name() != null && row.name().trim().toLowerCase(Locale.ROOT).equals(wanted)) {
                throw new ApiException(409, "Store name already exists");
            }
        }
    }

    private static String name(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            throw new ApiException(400, "Store name is required");
        }
        if (name.length() > 150) {
            throw new ApiException(400, "Store name must be at most 150 characters");
        }
        return name;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireVersion(StoreRow row, long expectedVersion) {
        if (row.version() != expectedVersion) {
            throw versionConflict();
        }
    }

    private static ApiException versionConflict() {
        return new ApiException(409, "Store version conflict");
    }

    static ApiException notFound() {
        return new ApiException(404, "Store not found");
    }
}
