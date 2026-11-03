-- D9: automatic Mining executes as a dedicated machine principal, not as the last System Admin who edited a schedule.
-- The row exists only for audit foreign keys (utility_snapshot.created_by, mining_request.requested_by, occurrence actor).
-- It has no password, no verified e-mail, no external identity and the non-loginable INACTIVE status; the account management APIs
-- hide it and refuse to change it, and no endpoint authorizes the SYSTEM_SCHEDULER role.
INSERT INTO role(code, display_name) VALUES ('SYSTEM_SCHEDULER', 'System Scheduler (machine principal)');
INSERT INTO app_user(role_id, email, name, status)
SELECT role_id, 'system-scheduler@paspm.invalid', 'PASPM Scheduled Mining', 'INACTIVE' FROM role WHERE code = 'SYSTEM_SCHEDULER';
