package vn.edu.fpt.paspm.storefront.cart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.storefront.StorefrontBundleService;
import vn.edu.fpt.paspm.storefront.StorefrontContracts.PublicStore;
import vn.edu.fpt.paspm.storefront.StorefrontRepository;
import vn.edu.fpt.paspm.storefront.StorefrontRepository.AvailabilityRow;
import vn.edu.fpt.paspm.storefront.StorefrontRepository.StoreRow;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.BundleAddResult;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.BundleComponentResult;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.CartLineState;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.CartLineView;
import vn.edu.fpt.paspm.storefront.cart.CartContracts.CartView;
import vn.edu.fpt.paspm.storefront.cart.CartRepository.Cart;
import vn.edu.fpt.paspm.storefront.cart.CartRepository.Event;
import vn.edu.fpt.paspm.storefront.cart.CartRepository.Line;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;

@Service
public class CartService {
    private static final int MAX_QUANTITY = 99;
    private static final BigDecimal EMPTY_SUBTOTAL = BigDecimal.ZERO.setScale(2);

    private final CartRepository carts;
    private final StorefrontRepository storefront;
    private final UtilityTimeZone timeZone;
    private final StorefrontBundleService bundles;

    CartService(CartRepository carts, StorefrontRepository storefront, UtilityTimeZone timeZone,
            StorefrontBundleService bundles) {
        this.carts = carts;
        this.storefront = storefront;
        this.timeZone = timeZone;
        this.bundles = bundles;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CartView get(String slug, int customerId) {
        StoreRow store = requireStore(slug);
        return view(store, carts.find(store.id(), customerId), timeZone.today());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartView add(String slug, int customerId, long version, int productId, int quantity) {
        carts.lockCustomer(customerId);
        StoreRow store = requireStore(slug);
        LocalDate today = timeZone.today();
        AvailabilityRow stock = requireActiveProduct(store, productId, today);
        Cart cart = carts.find(store.id(), customerId);
        checkVersion(cart, version);
        Line line = cart == null ? null : carts.lineByProduct(store.id(), cart.id(), productId);
        long target = (line == null ? 0L : line.quantity()) + quantity;
        checkQuantity(target, stock);
        if (cart == null) cart = carts.create(store.id(), customerId);
        if (line == null) {
            carts.insertLine(store.id(), cart.id(), productId, (int) target);
        } else {
            carts.updateQuantity(store.id(), cart.id(), line.id(), (int) target);
        }
        return changed(store, customerId, cart, Event.ITEM_ADDED, today);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = BundleRejected.class)
    public BundleAddResult addBundle(String slug, int customerId, long version, String key) {
        carts.lockCustomer(customerId);
        StoreRow store = storefront.publicStoreForUpdate(slug);
        if (store == null) throw new ApiException(404, "Storefront not found");
        var bundle = bundles.resolve(store.id(), key);
        Cart cart = carts.find(store.id(), customerId);
        checkVersion(cart, version);
        LocalDate today = timeZone.today();
        Map<Integer, AvailabilityRow> stock = storefront.availability(store.id(), bundle.productIds(), today);
        Map<Integer, Line> existing = cart == null ? Map.of() : carts.lines(store.id(), cart.id()).stream()
                .collect(Collectors.toMap(Line::productId, Function.identity()));
        List<BundleComponentResult> components = bundle.productIds().stream()
                .map(productId -> bundleComponent(productId, stock.get(productId), existing.get(productId)))
                .toList();
        int total = components.stream().mapToInt(BundleComponentResult::added).sum();
        if (total == 0) {
            carts.recordBundle(store.id(), cart == null ? null : cart.id(), bundle, 0);
            throw new BundleRejected(components);
        }

        if (cart == null) cart = carts.create(store.id(), customerId);
        for (BundleComponentResult component : components) {
            if (component.added() == 0) continue;
            Line line = existing.get(component.productId());
            if (line == null) {
                carts.insertLine(store.id(), cart.id(), component.productId(), component.added(), true);
            } else {
                carts.addBundleToLine(store.id(), cart.id(), line.id(), line.quantity() + component.added());
            }
        }
        carts.advanceVersion(store.id(), cart.id());
        carts.recordBundle(store.id(), cart.id(), bundle, total);
        return new BundleAddResult(view(store, carts.find(store.id(), customerId), today), components);
    }

    private static BundleComponentResult bundleComponent(int productId, AvailabilityRow stock, Line line) {
        int quantity = line == null ? 0 : line.quantity();
        long available = stock == null || !stock.productActive() ? 0 : stock.sellableQuantity();
        long increment = Math.min(Math.max(0, available - quantity), MAX_QUANTITY - quantity);
        return new BundleComponentResult(productId, 1, (int) Math.min(1, increment));
    }

    static final class BundleRejected extends ApiException {
        private final List<BundleComponentResult> components;

        BundleRejected(List<BundleComponentResult> components) {
            super(409, "No bundle component can be added", "BUNDLE_ADD_REJECTED");
            this.components = components;
        }

        List<BundleComponentResult> components() {
            return components;
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartView changeQuantity(String slug, int customerId, long version, long itemId, int quantity) {
        carts.lockCustomer(customerId);
        StoreRow store = requireStore(slug);
        Cart cart = carts.find(store.id(), customerId);
        Line line = requireLine(store, cart, itemId);
        checkVersion(cart, version);
        LocalDate today = timeZone.today();
        if (quantity == line.quantity()) return view(store, cart, today);
        if (quantity > line.quantity()) {
            checkQuantity(quantity, requireActiveProduct(store, line.productId(), today));
        }
        carts.updateQuantity(store.id(), cart.id(), line.id(), quantity);
        return changed(store, customerId, cart, Event.ITEM_QUANTITY_CHANGED, today);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartView remove(String slug, int customerId, long version, long itemId) {
        carts.lockCustomer(customerId);
        StoreRow store = requireStore(slug);
        Cart cart = carts.find(store.id(), customerId);
        Line line = requireLine(store, cart, itemId);
        checkVersion(cart, version);
        carts.deleteLine(store.id(), cart.id(), line.id());
        return changed(store, customerId, cart, Event.ITEM_REMOVED, timeZone.today());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CartView clear(String slug, int customerId, long version) {
        carts.lockCustomer(customerId);
        StoreRow store = requireStore(slug);
        Cart cart = carts.find(store.id(), customerId);
        checkVersion(cart, version);
        LocalDate today = timeZone.today();
        if (cart == null || carts.clear(store.id(), cart.id()) == 0) return view(store, cart, today);
        return changed(store, customerId, cart, Event.CART_CLEARED, today);
    }

    private StoreRow requireStore(String slug) {
        StoreRow store = storefront.publicStore(slug);
        if (store == null) throw new ApiException(404, "Storefront not found");
        return store;
    }

    private void checkVersion(Cart cart, long expectedVersion) {
        long actualVersion = cart == null ? 0 : cart.version();
        if (actualVersion != expectedVersion) {
            throw new ApiException(409, "Cart version changed; reload the cart", "CART_VERSION_CONFLICT");
        }
    }

    private Line requireLine(StoreRow store, Cart cart, long itemId) {
        Line line = cart == null ? null : carts.lineById(store.id(), cart.id(), itemId);
        if (line == null) throw new ApiException(404, "Cart item not found");
        return line;
    }

    private AvailabilityRow requireActiveProduct(StoreRow store, int productId, LocalDate today) {
        AvailabilityRow stock = storefront.availability(store.id(), List.of(productId), today).get(productId);
        if (stock == null || !stock.productActive()) throw new ApiException(404, "Product not found");
        return stock;
    }

    private void checkQuantity(long quantity, AvailabilityRow stock) {
        if (quantity > MAX_QUANTITY) {
            throw new ApiException(409, "A cart can hold at most 99 units per product", "CART_QUANTITY_LIMIT");
        }
        if (quantity > stock.sellableQuantity()) {
            throw new ApiException(409, "Insufficient sellable stock", "INSUFFICIENT_SELLABLE_STOCK");
        }
    }

    private CartView changed(StoreRow store, int customerId, Cart cart, Event event, LocalDate today) {
        carts.recordChange(store.id(), cart.id(), event);
        return view(store, carts.find(store.id(), customerId), today);
    }

    private CartView view(StoreRow store, Cart cart, LocalDate today) {
        PublicStore publicStore = store.toPublic();
        if (cart == null) return new CartView(null, publicStore, 0, "VND", null, List.of(), EMPTY_SUBTOTAL);
        List<Line> rows = carts.lines(store.id(), cart.id());
        Map<Integer, AvailabilityRow> inventory = storefront.availability(store.id(),
                rows.stream().map(Line::productId).toList(), today);
        List<CartLineView> items = rows.stream().map(line -> lineView(line, inventory.get(line.productId()))).toList();
        BigDecimal subtotal = EMPTY_SUBTOTAL;
        for (CartLineView item : items) {
            if (item.lineTotal() == null) {
                subtotal = null;
                break;
            }
            subtotal = subtotal.add(item.lineTotal());
        }
        return new CartView(cart.id(), publicStore, cart.version(), "VND", cart.updatedAt(), items, subtotal);
    }

    private static CartLineView lineView(Line line, AvailabilityRow stock) {
        if (stock == null || !stock.productActive() || line.price() == null) {
            return new CartLineView(line.id(), line.productId(), null, null, null, line.quantity(),
                    null, null, CartLineState.NO_LONGER_AVAILABLE, line.fromBundle());
        }
        CartLineState state = stock.sellableQuantity() > 0 ? CartLineState.AVAILABLE : CartLineState.OUT_OF_STOCK;
        return new CartLineView(line.id(), line.productId(), line.name(), line.imageUrl(), line.unit(), line.quantity(),
                line.price(), line.price().multiply(BigDecimal.valueOf(line.quantity())), state, line.fromBundle());
    }
}
