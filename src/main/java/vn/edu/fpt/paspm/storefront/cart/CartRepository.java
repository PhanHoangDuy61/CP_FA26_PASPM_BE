package vn.edu.fpt.paspm.storefront.cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.storefront.StorefrontBundleService.Bundle;

@Repository
class CartRepository {
    private static final String LINES = """
            SELECT i.item_id,i.product_id,i.quantity,i.added_from_bundle,p.name,p.image_url,p.unit,p.selling_price
            FROM shopping_cart_item i
            LEFT JOIN product p ON p.store_id=i.store_id AND p.product_id=i.product_id
                AND p.status='ACTIVE' AND p.deleted_at IS NULL
            WHERE i.store_id=? AND i.cart_id=?
            """;

    record Cart(long id, long version, Instant updatedAt) {}
    record Line(long id, int productId, int quantity, boolean fromBundle, String name, String imageUrl,
            String unit, BigDecimal price) {}
    enum Event { ITEM_ADDED, ITEM_QUANTITY_CHANGED, ITEM_REMOVED, CART_CLEARED, BUNDLE_ADDED, BUNDLE_ADD_REJECTED }

    private final JdbcTemplate jdbc;

    CartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // An existing row provides a common lock even before a Store cart has been created.
    void lockCustomer(int customerId) {
        jdbc.queryForObject("SELECT user_id FROM app_user WHERE user_id=? FOR UPDATE", Integer.class, customerId);
    }

    Cart find(int storeId, int customerId) {
        return jdbc.query("""
                SELECT cart_id,version,updated_at FROM shopping_cart WHERE store_id=? AND customer_user_id=?
                """, (rs, row) -> new Cart(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toInstant()),
                storeId, customerId).stream().findFirst().orElse(null);
    }

    Cart create(int storeId, int customerId) {
        jdbc.update("INSERT INTO shopping_cart(store_id,customer_user_id) VALUES (?,?)", storeId, customerId);
        return find(storeId, customerId);
    }

    List<Line> lines(int storeId, long cartId) {
        return jdbc.query(LINES + " ORDER BY i.item_id", CartRepository::mapLine, storeId, cartId);
    }

    Line lineById(int storeId, long cartId, long itemId) {
        return jdbc.query(LINES + " AND i.item_id=?", CartRepository::mapLine, storeId, cartId, itemId)
                .stream().findFirst().orElse(null);
    }

    Line lineByProduct(int storeId, long cartId, int productId) {
        return jdbc.query(LINES + " AND i.product_id=?", CartRepository::mapLine, storeId, cartId, productId)
                .stream().findFirst().orElse(null);
    }

    private static Line mapLine(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Line(rs.getLong("item_id"), rs.getInt("product_id"), rs.getInt("quantity"),
                rs.getBoolean("added_from_bundle"), rs.getString("name"), rs.getString("image_url"),
                rs.getString("unit"), rs.getBigDecimal("selling_price"));
    }

    void insertLine(int storeId, long cartId, int productId, int quantity) {
        insertLine(storeId, cartId, productId, quantity, false);
    }

    void insertLine(int storeId, long cartId, int productId, int quantity, boolean fromBundle) {
        jdbc.update("""
                INSERT INTO shopping_cart_item(store_id,cart_id,product_id,quantity,added_from_bundle)
                VALUES (?,?,?,?,?)
                """, storeId, cartId, productId, quantity, fromBundle);
    }

    void updateQuantity(int storeId, long cartId, long itemId, int quantity) {
        jdbc.update("UPDATE shopping_cart_item SET quantity=? WHERE store_id=? AND cart_id=? AND item_id=?",
                quantity, storeId, cartId, itemId);
    }

    void addBundleToLine(int storeId, long cartId, long itemId, int quantity) {
        jdbc.update("""
                UPDATE shopping_cart_item SET quantity=?,added_from_bundle=TRUE
                WHERE store_id=? AND cart_id=? AND item_id=?
                """, quantity, storeId, cartId, itemId);
    }

    void deleteLine(int storeId, long cartId, long itemId) {
        jdbc.update("DELETE FROM shopping_cart_item WHERE store_id=? AND cart_id=? AND item_id=?",
                storeId, cartId, itemId);
    }

    int clear(int storeId, long cartId) {
        return jdbc.update("DELETE FROM shopping_cart_item WHERE store_id=? AND cart_id=?", storeId, cartId);
    }

    void advanceVersion(int storeId, long cartId) {
        jdbc.update("""
                UPDATE shopping_cart SET version=version+1,updated_at=CURRENT_TIMESTAMP(6)
                WHERE store_id=? AND cart_id=?
                """, storeId, cartId);
    }

    void recordChange(int storeId, long cartId, Event event) {
        advanceVersion(storeId, cartId);
        jdbc.update("INSERT INTO storefront_event(store_id,cart_id,event_type) VALUES (?,?,?)",
                storeId, cartId, event.name());
    }

    void recordBundle(int storeId, Long cartId, Bundle bundle, int addedUnits) {
        boolean rejected = addedUnits == 0;
        Event event = rejected ? Event.BUNDLE_ADD_REJECTED : Event.BUNDLE_ADDED;
        jdbc.update("""
                INSERT INTO storefront_event(store_id,cart_id,event_type,campaign_id,layout_id,layout_version,
                    block_no,bundle_origin,added_product_units,failure_code)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, storeId, cartId, event.name(), bundle.campaignId(), bundle.layoutId(), bundle.layoutVersion(),
                bundle.blockNo(), bundle.origin().name(), addedUnits, rejected ? event.name() : null);
    }
}
