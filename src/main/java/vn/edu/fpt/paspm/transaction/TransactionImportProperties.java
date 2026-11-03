package vn.edu.fpt.paspm.transaction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TransactionImportProperties {
    private final long maxFileBytes;
    private final int maxRows;

    public TransactionImportProperties(
            @Value("${paspm.transaction.import.max-file-bytes:5242880}") long maxFileBytes,
            @Value("${paspm.transaction.import.max-rows:10000}") int maxRows) {
        if (maxFileBytes < 1 || maxRows < 1) throw new IllegalArgumentException("F04 import limits must be positive");
        this.maxFileBytes = maxFileBytes;
        this.maxRows = maxRows;
    }

    public long maxFileBytes() { return maxFileBytes; }
    public int maxRows() { return maxRows; }
}
