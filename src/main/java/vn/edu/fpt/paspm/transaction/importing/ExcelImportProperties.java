package vn.edu.fpt.paspm.transaction.importing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Technical parsing bounds, independent from the shared file/order business limits. */
@Component
public record ExcelImportProperties(long maxExpandedBytes, long maxEntryBytes, int maxEntries,
        int maxCellCharacters, int maxSharedStrings, int maxStyles, int maxScannedRows) {
    public ExcelImportProperties(
            @Value("${paspm.transaction.import.excel.max-expanded-bytes:33554432}") long maxExpandedBytes,
            @Value("${paspm.transaction.import.excel.max-entry-bytes:8388608}") long maxEntryBytes,
            @Value("${paspm.transaction.import.excel.max-entries:128}") int maxEntries,
            @Value("${paspm.transaction.import.excel.max-cell-characters:32767}") int maxCellCharacters,
            @Value("${paspm.transaction.import.excel.max-shared-strings:100000}") int maxSharedStrings,
            @Value("${paspm.transaction.import.excel.max-styles:1024}") int maxStyles,
            @Value("${paspm.transaction.import.excel.max-scanned-rows:100000}") int maxScannedRows) {
        if (maxExpandedBytes < 1 || maxEntryBytes < 1 || maxEntryBytes > maxExpandedBytes
                || maxEntries < 1 || maxCellCharacters < 1 || maxCellCharacters > 32767
                || maxSharedStrings < 1 || maxStyles < 1 || maxScannedRows < 1) {
            throw new IllegalArgumentException("Excel import limits must be positive and internally consistent");
        }
        this.maxExpandedBytes = maxExpandedBytes;
        this.maxEntryBytes = maxEntryBytes;
        this.maxEntries = maxEntries;
        this.maxCellCharacters = maxCellCharacters;
        this.maxSharedStrings = maxSharedStrings;
        this.maxStyles = maxStyles;
        this.maxScannedRows = maxScannedRows;
    }

    public static ExcelImportProperties defaults() {
        return new ExcelImportProperties(33_554_432, 8_388_608, 128, 32767, 100000, 1024, 100000);
    }
}
