package vn.edu.fpt.paspm.transaction.importing;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.transaction.TransactionContracts.ImportSummary;
import vn.edu.fpt.paspm.transaction.TransactionImportProperties;
import vn.edu.fpt.paspm.transaction.importing.TransactionCsvParser.ParsedCsvRow;
import vn.edu.fpt.paspm.transaction.importing.TransactionOrderValidator.ValidatedLine;
import vn.edu.fpt.paspm.transaction.importing.TransactionOrderValidator.ValidatedOrder;

@Service
public class TransactionImportService {
    private static final String SOURCE_SYSTEM = "CSV_UPLOAD";
    private final CatalogAccess access;
    private final TransactionCsvParser parser;
    private final TransactionOrderValidator validator;
    private final TransactionImportRepository imports;
    private final TransactionImportProperties properties;
    private final TransactionTemplate orderTransaction;

    public TransactionImportService(CatalogAccess access, TransactionCsvParser parser,
            TransactionOrderValidator validator, TransactionImportRepository imports,
            TransactionImportProperties properties, PlatformTransactionManager transactionManager) {
        this.access = access;
        this.parser = parser;
        this.validator = validator;
        this.imports = imports;
        this.properties = properties;
        this.orderTransaction = new TransactionTemplate(transactionManager);
        this.orderTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ImportSummary importCsv(Jwt jwt, MultipartFile file) {
        int storeId = access.currentStoreId(jwt);
        validateFile(file);
        int batchId = imports.createBatch(storeId, filename(file.getOriginalFilename()));
        List<ParsedCsvRow> rows = new ArrayList<>();
        try (var stream = file.getInputStream()) {
            parser.parse(stream, rows::add);
            if (rows.isEmpty()) throw new ApiException(400, "CSV must contain at least one data row");
        } catch (ApiException exception) {
            imports.failBatch(batchId, exception.getMessage());
            throw exception;
        } catch (IOException exception) {
            imports.failBatch(batchId, "Unable to read uploaded CSV");
            throw new ApiException(400, "Unable to read uploaded CSV");
        }

        try {
            imports.insertStagingRows(storeId, batchId, rows);
            List<ValidatedOrder> orders = validator.validateAndGroup(storeId, rows);
            int validRows = 0;
            int rejectedRows = 0;
            int acceptedOrders = 0;
            int rejectedOrders = 0;
            int duplicateOrders = 0;
            int conflictOrders = 0;

            for (ValidatedOrder order : orders) {
                if (!order.valid()) {
                    imports.markOrderRejected(batchId, order);
                    rejectedRows += order.rowNumbers().size();
                    rejectedOrders++;
                    continue;
                }

                validRows += order.rowNumbers().size();
                Map<Integer, Integer> productIdsByRow = productIdsByRow(order);
                imports.markOrderValid(batchId, order, productIdsByRow);
                String contentHash = contentHash(order);
                OrderOutcome outcome = persistOrder(storeId, batchId, order, contentHash, productIdsByRow);
                switch (outcome.kind()) {
                    case ACCEPTED -> acceptedOrders++;
                    case DUPLICATE -> duplicateOrders++;
                    case CONFLICT -> {
                        validRows -= order.rowNumbers().size();
                        rejectedRows += order.rowNumbers().size();
                        conflictOrders++;
                    }
                }
            }

            imports.finishBatch(batchId, validRows, rejectedRows, orders.size(), acceptedOrders, rejectedOrders,
                    duplicateOrders, conflictOrders);
            return imports.getBatch(storeId, batchId);
        } catch (RuntimeException exception) {
            imports.failBatch(batchId, "Import processing failed; retrying the same file is safe");
            throw exception;
        }
    }

    private OrderOutcome persistOrder(int storeId, int batchId, ValidatedOrder order, String contentHash,
            Map<Integer, Integer> productIdsByRow) {
        try {
            return orderTransaction.execute(status -> persistOrderInTransaction(
                    storeId, batchId, order, contentHash, productIdsByRow));
        } catch (DuplicateKeyException exception) {
            TransactionImportRepository.ExistingOrder existing = imports.findExisting(storeId, order.sourceOrderId());
            if (existing == null) throw exception;
            return orderTransaction.execute(status -> handleExistingOrder(batchId, storeId, order, contentHash,
                    productIdsByRow, existing));
        }
    }

    private OrderOutcome persistOrderInTransaction(int storeId, int batchId, ValidatedOrder order, String contentHash,
            Map<Integer, Integer> productIdsByRow) {
        TransactionImportRepository.ExistingOrder existing = imports.findExisting(storeId, order.sourceOrderId());
        if (existing != null) return handleExistingOrder(batchId, storeId, order, contentHash, productIdsByRow, existing);

        BigDecimal total = order.lines().stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        int transactionId = imports.insertTransaction(storeId, batchId, order, contentHash, total);
        Map<Integer, Integer> itemIds = imports.insertItems(storeId, transactionId, order.lines());
        imports.markProcessed(batchId, order, transactionId, itemIds, productIdsByRow);
        return new OrderOutcome(OutcomeKind.ACCEPTED);
    }

    private OrderOutcome handleExistingOrder(int batchId, int storeId, ValidatedOrder order, String contentHash,
            Map<Integer, Integer> productIdsByRow, TransactionImportRepository.ExistingOrder existing) {
        if (existing.contentHash().equals(contentHash)) {
            Map<Integer, Integer> itemIds = imports.findItemIds(storeId, existing.transactionId());
            imports.markProcessed(batchId, order, existing.transactionId(), itemIds, productIdsByRow);
            return new OrderOutcome(OutcomeKind.DUPLICATE);
        }
        imports.markConflict(batchId, order, "Source order ID already exists with different content");
        return new OrderOutcome(OutcomeKind.CONFLICT);
    }

    private Map<Integer, Integer> productIdsByRow(ValidatedOrder order) {
        Map<Integer, Integer> productByRow = new LinkedHashMap<>();
        for (ValidatedLine line : order.lines()) {
            for (int rowNumber : line.sourceRowNumbers()) productByRow.put(rowNumber, line.productId());
        }
        return productByRow;
    }

    private String contentHash(ValidatedOrder order) {
        StringBuilder canonical = new StringBuilder()
                .append(order.sourceOrderId()).append('|').append(order.occurredAt()).append('|').append(order.status());
        order.lines().stream().sorted(Comparator.comparingInt(ValidatedLine::productId)).forEach(line -> canonical
                .append('\n').append(line.productId()).append('|').append(line.quantity()).append('|')
                .append(line.unitPrice().stripTrailingZeros().toPlainString()).append('|')
                .append(line.unitCost().stripTrailingZeros().toPlainString()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(400, "CSV file is required and cannot be empty");
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ApiException(400, "Only .csv files are supported");
        }
        if (file.getSize() > properties.maxFileBytes()) throw new ApiException(413, "Uploaded file exceeds the configured limit");
        String contentType = file.getContentType();
        String mediaType = contentType == null ? null : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (mediaType != null && !List.of("text/csv", "application/csv", "application/vnd.ms-excel", "application/octet-stream")
                .contains(mediaType)) {
            throw new ApiException(400, "Uploaded file must have a CSV content type");
        }
    }

    private String filename(String originalName) {
        String leaf = originalName.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1);
        return leaf.substring(0, Math.min(255, leaf.length()));
    }

    private enum OutcomeKind { ACCEPTED, DUPLICATE, CONFLICT }
    private record OrderOutcome(OutcomeKind kind) {}
}
