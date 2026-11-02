package vn.edu.fpt.paspm.mining;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;

/**
 * Decision D9: the machine identity that executes scheduled Mining (migration V19). It is an audit identity only: it is never
 * ACTIVE, has no credential and cannot authenticate, so "who ran it" no longer depends on any human administrator's status.
 */
@Component
public class SchedulerPrincipal {
    public static final String ROLE = vn.edu.fpt.paspm.user.UserRepository.SYSTEM_PRINCIPAL_ROLE;
    public static final String EMAIL = "system-scheduler@paspm.invalid";
    private final JdbcTemplate jdbc;
    public SchedulerPrincipal(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** The principal's user id, resolved from the database on every call. */
    public int id() {
        var ids = jdbc.queryForList("SELECT u.user_id FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code=? AND u.email=?", Integer.class, ROLE, EMAIL);
        if (ids.size() != 1) throw new ApiException(409, "Scheduler principal unavailable");
        return ids.get(0);
    }
    /** An occurrence may be prepared or submitted only on behalf of the scheduler principal. */
    public void require(int actor) {
        Integer matches = jdbc.queryForObject("SELECT COUNT(*) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE u.user_id=? AND r.code=? AND u.email=?", Integer.class, actor, ROLE, EMAIL);
        if (matches == null || matches != 1) throw new ApiException(403, "Occurrence actor is not the scheduler principal");
    }
}
