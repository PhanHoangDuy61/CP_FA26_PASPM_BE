package vn.edu.fpt.paspm.transaction.importing;

import java.io.IOException;
import java.io.ByteArrayInputStream;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.transaction.TransactionContracts.ImportSummary;
import vn.edu.fpt.paspm.transaction.TransactionContracts.ImportProgress;
import vn.edu.fpt.paspm.transaction.TransactionImportProperties;
import vn.edu.fpt.paspm.transaction.importing.TransactionCsvParser.ParsedCsvRow;
import vn.edu.fpt.paspm.transaction.importing.TransactionOrderValidator.ValidatedLine;
import vn.edu.fpt.paspm.transaction.importing.TransactionOrderValidator.ValidatedOrder;

@Service
public class TransactionImportService {
    private final CatalogAccess access;
    private final TransactionCsvParser parser;
    private final TransactionOrderValidator validator;
    private final TransactionImportRepository imports;
    private final TransactionImportProperties properties;
    private final TransactionTemplate orderTransaction;
    private final TransactionTemplate batchTransaction;
    private final TransactionImportLease leases;

    public TransactionImportService(CatalogAccess access, TransactionCsvParser parser,
            TransactionOrderValidator validator, TransactionImportRepository imports,
            TransactionImportProperties properties, TransactionImportLease leases,
            PlatformTransactionManager transactionManager) {
        this.access = access;
        this.parser = parser;
        this.validator = validator;
        this.imports = imports;
        this.properties = properties;
        this.leases = leases;
        this.orderTransaction = new TransactionTemplate(transactionManager);
        this.orderTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchTransaction = new TransactionTemplate(transactionManager);
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
            processRows(storeId, batchId, rows, null);
            return imports.getBatch(storeId, batchId);
        } catch (RuntimeException exception) {
            imports.failBatch(batchId, "Import processing failed; retrying the same file is safe");
            throw exception;
        }
    }

    public ImportProgress submitAsync(Jwt jwt, MultipartFile file, String idempotencyKey) {
        int storeId = access.currentStoreId(jwt);
        validateFile(file);
        String key = validateIdempotencyKey(idempotencyKey);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(400, "Unable to read uploaded CSV");
        }
        String fileHash = sha256(content);
        TransactionImportRepository.ExistingBatch previous = imports.findIdempotentBatch(storeId, key);
        if (previous != null) return sameAsyncRequest(storeId, previous, fileHash);

        List<ParsedCsvRow> rows = new ArrayList<>();
        try {
            parser.parse(new ByteArrayInputStream(content), rows::add);
        } catch (ApiException exception) {
            return persistRejectedAsyncRequest(storeId, filename(file.getOriginalFilename()), key,
                    fileHash, exception.getMessage(), null);
        }
        if (rows.isEmpty()) {
            return persistRejectedAsyncRequest(storeId, filename(file.getOriginalFilename()), key,
                    fileHash, "CSV must contain at least one data row", 0);
        }

        try {
            return batchTransaction.execute(transaction -> {
                int batchId = imports.createAsyncBatch(storeId, filename(file.getOriginalFilename()), key, fileHash);
                imports.insertStagingRows(storeId, batchId, rows);
                imports.queueBatch(storeId, batchId, rows.size());
                return imports.getProgress(storeId, batchId);
            });
        } catch (DuplicateKeyException exception) {
            return existingAsyncRequest(storeId, key, fileHash, exception);
        }
    }

    public void processBatch(TransactionImportLease.Claim claim) {
        List<ParsedCsvRow> rows = imports.stagingRows(claim.storeId(), claim.batchId());
        if (rows.isEmpty()) throw new IllegalStateException("Queued import batch has no staged rows");
        processRows(claim.storeId(), claim.batchId(), rows, claim);
    }

    private ImportProgress persistRejectedAsyncRequest(int storeId, String filename, String key, String fileHash,
            String reason, Integer totalCount) {
        try {
            return batchTransaction.execute(transaction -> {
                int batchId = imports.createAsyncBatch(storeId, filename, key, fileHash);
                if (totalCount != null) imports.setInputTotalCount(storeId, batchId, totalCount);
                imports.failBatch(batchId, reason);
                return imports.getProgress(storeId, batchId);
            });
        } catch (DuplicateKeyException exception) {
            return existingAsyncRequest(storeId, key, fileHash, exception);
        }
    }

    private ImportProgress existingAsyncRequest(int storeId, String key, String fileHash,
            DuplicateKeyException original) {
        TransactionImportRepository.ExistingBatch existing = imports.findIdempotentBatch(storeId, key);
        if (existing == null) throw original;
        return sameAsyncRequest(storeId, existing, fileHash);
    }

    private ImportProgress sameAsyncRequest(int storeId, TransactionImportRepository.ExistingBatch previous,
            String fileHash) {
        if (!previous.contentHash().equals(fileHash)) {
            throw new ApiException(409, "Idempotency-Key already used for a different CSV file");
        }
        ImportProgress progress = imports.getProgress(storeId, previous.batchId());
        if (progress == null) throw new IllegalStateException("Idempotent import batch disappeared");
        return progress;
    }

    private void processRows(int storeId, int batchId, List<ParsedCsvRow> rows,
            TransactionImportLease.Claim claim) {
        List<ValidatedOrder> orders = validator.validateAndGroup(storeId, rows);
        if (claim == null) {
            imports.assignOrderGroups(storeId, batchId, orders);
        } else {
            leases.guarded(claim, () -> {
                imports.assignOrderGroups(storeId, batchId, orders);
                imports.touchBatch(claim);
            });
        }
        int validRows = 0;
        int rejectedRows = 0;
        int acceptedOrders = 0;
        int rejectedOrders = 0;
        int duplicateOrders = 0;
        int conflictOrders = 0;

        for (ValidatedOrder order : orders) {
            if (!order.valid()) {
                if (claim == null) {
                    imports.markOrderRejected(batchId, order);
                } else {
                    leases.guarded(claim, () -> {
                        imports.markOrderRejected(batchId, order);
                        imports.touchBatch(claim);
                    });
                }
                rejectedRows += order.rowNumbers().size();
                rejectedOrders++;
                continue;
            }

            validRows += order.rowNumbers().size();
            Map<Integer, Integer> productIdsByRow = productIdsByRow(order);
            String contentHash = contentHash(order);
            OrderOutcome outcome = persistOrder(storeId, batchId, order, contentHash, productIdsByRow, claim);
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

        if (claim == null) {
            imports.finishBatch(batchId, validRows, rejectedRows, orders.size(), acceptedOrders, rejectedOrders,
                    duplicateOrders, conflictOrders);
        } else {
            int finalValidRows = validRows;
            int finalRejectedRows = rejectedRows;
            int finalAcceptedOrders = acceptedOrders;
            int finalRejectedOrders = rejectedOrders;
            int finalDuplicateOrders = duplicateOrders;
            int finalConflictOrders = conflictOrders;
            leases.terminal(claim, () -> imports.finishBatch(claim, finalValidRows, finalRejectedRows, orders.size(),
                    finalAcceptedOrders, finalRejectedOrders, finalDuplicateOrders, finalConflictOrders));
        }
    }

    private OrderOutcome persistOrder(int storeId, int batchId, ValidatedOrder order, String contentHash,
            Map<Integer, Integer> productIdsByRow, TransactionImportLease.Claim claim) {
        try {
            return orderTransaction.execute(status -> persistOrderInTransaction(
                    storeId, batchId, order, contentHash, productIdsByRow, claim));
        } catch (DuplicateKeyException exception) {
            TransactionImportRepository.ExistingOrder existing = imports.findExisting(storeId, order.sourceOrderId());
            if (existing == null) throw exception;
            return orderTransaction.execute(status -> {
                assertLease(claim);
                imports.markOrderValid(batchId, order, productIdsByRow);
                OrderOutcome outcome = handleExistingOrder(batchId, storeId, order, contentHash,
                        productIdsByRow, existing);
                touchBatch(claim);
                return outcome;
            });
        }
    }

    private OrderOutcome persistOrderInTransaction(int storeId, int batchId, ValidatedOrder order, String contentHash,
            Map<Integer, Integer> productIdsByRow, TransactionImportLease.Claim claim) {
        assertLease(claim);
        imports.markOrderValid(batchId, order, productIdsByRow);
        TransactionImportRepository.ExistingOrder existing = imports.findExisting(storeId, order.sourceOrderId());
        if (existing != null) {
            OrderOutcome outcome = handleExistingOrder(batchId, storeId, order, contentHash, productIdsByRow, existing);
            touchBatch(claim);
            return outcome;
        }

        BigDecimal total = order.lines().stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        int transactionId = imports.insertTransaction(storeId, batchId, order, contentHash, total);
        Map<Integer, Integer> itemIds = imports.insertItems(storeId, transactionId, order.lines());
        imports.markProcessed(batchId, order, transactionId, itemIds, productIdsByRow, "ACCEPTED");
        touchBatch(claim);
        return new OrderOutcome(OutcomeKind.ACCEPTED);
    }

    private OrderOutcome handleExistingOrder(int batchId, int storeId, ValidatedOrder order, String contentHash,
            Map<Integer, Integer> productIdsByRow, TransactionImportRepository.ExistingOrder existing) {
        if (existing.contentHash().equals(contentHash)) {
            Map<Integer, Integer> itemIds = imports.findItemIds(storeId, existing.transactionId());
            boolean sameBatch = existing.importBatchId() == batchId;
            imports.markProcessed(batchId, order, existing.transactionId(), itemIds, productIdsByRow,
                    sameBatch ? "ACCEPTED" : "DUPLICATE");
            return new OrderOutcome(sameBatch ? OutcomeKind.ACCEPTED : OutcomeKind.DUPLICATE);
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

    private String validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new ApiException(400, "Idempotency-Key must contain 1 to 128 URL-safe characters");
        }
        return key.toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void assertLease(TransactionImportLease.Claim claim) {
        if (claim != null) leases.assertCurrent(claim);
    }

    private void touchBatch(TransactionImportLease.Claim claim) {
        if (claim != null) imports.touchBatch(claim);
    }

    private String filename(String originalName) {
        String leaf = originalName.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1);
        return leaf.substring(0, Math.min(255, leaf.length()));
    }

    private enum OutcomeKind { ACCEPTED, DUPLICATE, CONFLICT }
    private record OrderOutcome(OutcomeKind kind) {}
}
