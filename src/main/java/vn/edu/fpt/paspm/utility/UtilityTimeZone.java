package vn.edu.fpt.paspm.utility;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** STORE has no timezone field; reuse the approved configured Store calendar convention. */
@Component
public class UtilityTimeZone {
    private final ZoneId zone;
    public UtilityTimeZone(@Value("${paspm.store-time-zone:Asia/Ho_Chi_Minh}") String zone) { this.zone=ZoneId.of(zone); }
    public String id() { return zone.getId(); }
    public LocalDate today() { return LocalDate.now(zone); }
}
