package vn.edu.fpt.paspm.utility;

import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;

@Component
@Profile("!utility-fixture")
public class UnavailableTransactionSource implements TransactionSource {
    public String sourceType() { return "F04_UNAVAILABLE"; }
    public List<PreparedTransaction> load(int storeId,LocalDate start,LocalDate end) {
        throw new ApiException(503,"F04 validated transaction provider is not implemented; no production data was substituted");
    }
}
