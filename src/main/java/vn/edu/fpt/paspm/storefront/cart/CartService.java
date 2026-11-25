package vn.edu.fpt.paspm.storefront.cart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.storefront.StorefrontContracts.PublicStore;
import vn.edu.fpt.paspm.storefront.StorefrontRepository;
import vn.edu.fpt.paspm.storefront.StorefrontRepository.AvailabilityRow;
import vn.edu.fpt.paspm.storefront.StorefrontRepository.StoreRow;
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

    CartService(CartRepository carts, StorefrontRepository storefront, UtilityTimeZone timeZone) {
        this.carts = carts;
        this.storefront = storefront;
        this.timeZone = timeZone;
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
        PublicStore publicStore = new PublicStore(store.slug(), store.name());
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
