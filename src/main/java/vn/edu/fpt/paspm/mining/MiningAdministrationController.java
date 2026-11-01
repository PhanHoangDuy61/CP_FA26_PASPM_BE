package vn.edu.fpt.paspm.mining;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.common.ApiException;

/** Global administrative policy. Existing requests retain their own frozen inputs. */
@RestController
@RequestMapping("/api/system/mining/config")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class MiningAdministrationController {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    public MiningAdministrationController(JdbcTemplate jdbc, AuthService auth) { this.jdbc=jdbc; this.auth=auth; }
    public record Request(@NotNull MiningEngine.Algorithm algorithm, @NotNull MiningContracts.MinUtilMode minUtilMode,
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=24,fraction=6) BigDecimal minUtilValue,
        @NotNull @Min(0) Long expectedVersion) {}
    public record View(long version, MiningEngine.Algorithm algorithm, MiningContracts.MinUtilMode minUtilMode,
        BigDecimal minUtilValue, Integer changedBy, Instant changedAt) {}
    @GetMapping
    @Transactional(readOnly=true)
    public View get() { return read(); }
    @PutMapping
    @Transactional
    public View update(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Request request) {
        if(request.minUtilMode()==MiningContracts.MinUtilMode.PERCENTAGE && request.minUtilValue().compareTo(BigDecimal.valueOf(100))>0)
            throw new ApiException(400,"Percentage minUtil must be in (0,100]");
        long version=jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1 FOR UPDATE",Long.class);
        if(version!=request.expectedVersion()) throw new ApiException(409,"Mining configuration version conflict");
        long next=Math.addExact(version,1);
        jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES (?,?,?,?,?)",next,request.algorithm().name(),request.minUtilMode().name(),request.minUtilValue(),auth.current(jwt).id());
        jdbc.update("UPDATE mining_config_head SET config_version=? WHERE config_id=1",next);
        return read();
    }
    private View read() {
        long version=jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1",Long.class);
        if(version==0) return new View(0,null,null,null,null,null);
        return jdbc.queryForObject("SELECT algorithm,min_util_mode,min_util_value,changed_by,changed_at FROM mining_config_version WHERE config_version=?",
            (rs,row)->new View(version,MiningEngine.Algorithm.valueOf(rs.getString(1)),MiningContracts.MinUtilMode.valueOf(rs.getString(2)),rs.getBigDecimal(3),rs.getInt(4),rs.getTimestamp(5).toInstant()),version);
    }
}
