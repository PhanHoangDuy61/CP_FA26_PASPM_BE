package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** F04 must supply only preprocessed business rows; source utility is already an item total. */
public interface TransactionSource {
    record Item(int productId,int quantity,BigDecimal unitPrice,BigDecimal unitCost,BigDecimal sourceUtility) {}
    record PreparedTransaction(int storeId,int transactionId,LocalDate occurredOn,boolean eligible,List<Item> items) {}
    String sourceType();
    List<PreparedTransaction> load(int storeId,LocalDate rangeStart,LocalDate rangeEnd);
}
