package vn.edu.fpt.paspm.transaction.importing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.transaction.importing.TransactionCsvParser.ParsedCsvRow;

@Component
public class TransactionOrderValidator {
    private static final Set<String> STATUSES = Set.of("COMPLETED", "CANCELLED", "REFUNDED", "PARTIAL_REFUND");
    private final JdbcTemplate jdbc;

    public TransactionOrderValidator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ValidatedOrder> validateAndGroup(int storeId, List<ParsedCsvRow> rows) {
        Map<String, MutableOrder> orders = new LinkedHashMap<>();
        List<ParsedRow> parsedRows = new ArrayList<>(rows.size());
        for (ParsedCsvRow row : rows) {
            Map<String, String> values = row.values();
            String sourceOrderId = trim(values.get("source_order_id"));
            String key = sourceOrderId == null || sourceOrderId.isEmpty() ? "\u0000row-" + row.rowNumber() : sourceOrderId;
            MutableOrder order = orders.computeIfAbsent(key, ignored -> new MutableOrder(sourceOrderId));
            order.rowNumbers.add(row.rowNumber());

            Instant occurredAt = parseOccurredAt(order, row, values.get("occurred_at"));
            String sku = requiredText(order, row, "sku", values.get("sku"), 80);
            Integer quantity = parseQuantity(order, row, values.get("quantity"));
            BigDecimal unitPrice = parseMoney(order, row, "unit_price", values.get("unit_price"));
            BigDecimal unitCost = parseMoney(order, row, "unit_cost", values.get("unit_cost"));
            String status = parseStatus(order, row, values.get("status"));

            if (sourceOrderId == null || sourceOrderId.isEmpty()) addError(order, row.rowNumber(), "source_order_id", "Order ID is required");
            else if (sourceOrderId.length() > 150) addError(order, row.rowNumber(), "source_order_id", "Order ID is too long");

            parsedRows.add(new ParsedRow(row.rowNumber(), order, occurredAt, sku, quantity, unitPrice, unitCost, status));
        }

        Map<String, Integer> productIds = resolveProducts(storeId, parsedRows);
        for (ParsedRow row : parsedRows) {
            MutableOrder order = row.order();
            checkOrderConsistency(order, row);
            if (row.sku() == null) continue;
            Integer productId = productIds.get(row.sku());
            if (productId == null) {
                addError(order, row.rowNumber(), "sku", "SKU does not belong to the authorized Store");
                continue;
            }
            if (row.quantity() == null || row.unitPrice() == null || row.unitCost() == null || row.status() == null) continue;
            addLine(order, row, productId);
        }

        return orders.values().stream().map(MutableOrder::freeze).toList();
    }

    private Map<String, Integer> resolveProducts(int storeId, List<ParsedRow> rows) {
        Set<String> skus = new LinkedHashSet<>();
        for (ParsedRow row : rows) if (row.sku() != null && !row.sku().isBlank()) skus.add(row.sku());
        if (skus.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(skus.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(storeId);
        arguments.addAll(skus);
        Map<String, Integer> products = new LinkedHashMap<>();
        jdbc.query("SELECT sku, product_id FROM product WHERE store_id=? AND sku IN (" + placeholders + ")",
                (rs, index) -> products.put(rs.getString("sku"), rs.getInt("product_id")), arguments.toArray());
        return products;
    }

    private Instant parseOccurredAt(MutableOrder order, ParsedCsvRow row, String value) {
        String text = trim(value);
        if (text == null || text.isEmpty()) {
            addError(order, row.rowNumber(), "occurred_at", "Timestamp is required");
            return null;
        }
        try { return OffsetDateTime.parse(text).toInstant(); }
        catch (DateTimeParseException exception) {
            addError(order, row.rowNumber(), "occurred_at", "Timestamp must be ISO-8601 with an explicit UTC offset");
            return null;
        }
    }

    private String requiredText(MutableOrder order, ParsedCsvRow row, String field, String value, int maxLength) {
        String text = trim(value);
        if (text == null || text.isEmpty()) {
            addError(order, row.rowNumber(), field, field + " is required");
            return null;
        }
        if (text.length() > maxLength) {
            addError(order, row.rowNumber(), field, field + " is too long");
            return null;
        }
        return text;
    }

    private Integer parseQuantity(MutableOrder order, ParsedCsvRow row, String value) {
        String text = trim(value);
        try {
            if (text == null || !text.matches("[0-9]+")) throw new NumberFormatException();
            int quantity = Integer.parseInt(text);
            if (quantity < 1) throw new NumberFormatException();
            return quantity;
        } catch (NumberFormatException exception) {
            addError(order, row.rowNumber(), "quantity", "Quantity must be a positive whole number");
            return null;
        }
    }

    private BigDecimal parseMoney(MutableOrder order, ParsedCsvRow row, String field, String value) {
        String text = trim(value);
        try {
            if (text == null || text.isEmpty()) throw new NumberFormatException();
            BigDecimal amount = new BigDecimal(text).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() < 0 || amount.precision() > 12) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException | ArithmeticException exception) {
            addError(order, row.rowNumber(), field, field + " must be a nonnegative decimal with at most two fraction digits");
            return null;
        }
    }

    private String parseStatus(MutableOrder order, ParsedCsvRow row, String value) {
        String text = trim(value);
        if (text == null || text.isEmpty()) {
            addError(order, row.rowNumber(), "status", "Status is required");
            return null;
        }
        String status = text.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            addError(order, row.rowNumber(), "status", "Status must be COMPLETED, CANCELLED, REFUNDED or PARTIAL_REFUND");
            return null;
        }
        return status;
    }

    private void checkOrderConsistency(MutableOrder order, ParsedRow row) {
        if (row.occurredAt() != null) {
            if (order.occurredAt != null && !order.occurredAt.equals(row.occurredAt()))
                addError(order, row.rowNumber(), "occurred_at", "Rows in one order must have the same timestamp");
            else order.occurredAt = row.occurredAt();
        }
        if (row.status() != null) {
            if (order.status != null && !order.status.equals(row.status()))
                addError(order, row.rowNumber(), "status", "Rows in one order must have the same status");
            else order.status = row.status();
        }
    }

    private void addLine(MutableOrder order, ParsedRow row, int productId) {
        AggregatedLine existing = order.lines.get(row.sku());
        if (existing == null) {
            order.lines.put(row.sku(), new AggregatedLine(productId, row.sku(), row.quantity(), row.unitPrice(), row.unitCost(), row.status(), row.rowNumber()));
            return;
        }
        if (existing.unitPrice.compareTo(row.unitPrice()) != 0 || existing.unitCost.compareTo(row.unitCost()) != 0
                || !existing.status.equals(row.status())) {
            addError(order, row.rowNumber(), "sku", "Repeated SKU lines must use the same price, cost and status");
            return;
        }
        try {
            existing.quantity = Math.addExact(existing.quantity, row.quantity());
            existing.rowNumbers.add(row.rowNumber());
        } catch (ArithmeticException exception) {
            addError(order, row.rowNumber(), "quantity", "Combined quantity exceeds the supported range");
        }
    }

    private void addError(MutableOrder order, int rowNumber, String field, String message) {
        order.errors.add(new ValidationError(rowNumber, field, message));
    }

    private String trim(String value) { return value == null ? null : value.trim(); }

    private record ParsedRow(int rowNumber, MutableOrder order, Instant occurredAt, String sku, Integer quantity,
            BigDecimal unitPrice, BigDecimal unitCost, String status) {}

    private static final class MutableOrder {
        final String sourceOrderId;
        final List<Integer> rowNumbers = new ArrayList<>();
        final List<ValidationError> errors = new ArrayList<>();
        final Map<String, AggregatedLine> lines = new LinkedHashMap<>();
        Instant occurredAt;
        String status;

        MutableOrder(String sourceOrderId) { this.sourceOrderId = sourceOrderId; }

        ValidatedOrder freeze() {
            List<ValidatedLine> frozenLines = lines.values().stream().map(AggregatedLine::freeze).toList();
            boolean eligible = errors.isEmpty() && "COMPLETED".equals(status)
                    && frozenLines.stream().allMatch(line -> line.unitPrice().compareTo(line.unitCost()) >= 0);
            return new ValidatedOrder(sourceOrderId, occurredAt, status, eligible, frozenLines,
                    List.copyOf(rowNumbers), List.copyOf(errors));
        }
    }

    private static final class AggregatedLine {
        final int productId;
        final String sku;
        int quantity;
        final BigDecimal unitPrice;
        final BigDecimal unitCost;
        final String status;
        final List<Integer> rowNumbers = new ArrayList<>();

        AggregatedLine(int productId, String sku, int quantity, BigDecimal unitPrice, BigDecimal unitCost, String status, int rowNumber) {
            this.productId = productId;
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.unitCost = unitCost;
            this.status = status;
            rowNumbers.add(rowNumber);
        }

        ValidatedLine freeze() { return new ValidatedLine(productId, sku, quantity, unitPrice, unitCost, List.copyOf(rowNumbers)); }
    }

    public record ValidationError(int rowNumber, String field, String message) {}
    public record ValidatedLine(int productId, String sku, int quantity, BigDecimal unitPrice, BigDecimal unitCost,
            List<Integer> sourceRowNumbers) {}
    public record ValidatedOrder(String sourceOrderId, Instant occurredAt, String status, boolean eligible,
            List<ValidatedLine> lines, List<Integer> rowNumbers, List<ValidationError> errors) {
        public boolean valid() { return errors.isEmpty() && !lines.isEmpty(); }
    }
}
