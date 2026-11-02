# F05 Scheduled Mining — native Linux acceptance, multi-instance, SIGKILL and SPMF cleanup (2026-11-02)

## 1. Verdict

**`NATIVE_LINUX_NOT_RUN`.** No native Linux host was available. The only Linux used was a Docker Desktop VM (kernel `6.10.14-linuxkit`) running a **linux/amd64 image under instruction emulation on Apple Silicon**. That is recorded below as *Linux, emulated, non-root*; it is useful evidence and it is **not** native acceptance, and no result here is labelled `NATIVE_LINUX_VERIFIED`.

What exists instead:

| Item | State |
|---|---|
| [`scripts/verify-native-linux.sh`](../../scripts/verify-native-linux.sh) — a reproducible runner for an approved Linux host or CI runner | written; preflight tested on macOS and on the emulated container, and **correctly refuses both** |
| The same suites executed on macOS arm64 | `LOCAL_VERIFIED`, section 3 |
| The same suites executed on Linux amd64 under emulation as a non-root user (uid 10001) | `LOCAL_VERIFIED` (not native), section 3 |
| Same suites on a real Linux kernel | **not run** |

Source: Backend branch `Khoi/feat/f05-scheduled-mining-option-b-completion`. Tested source tree: see the exact commits in section 3; production code differs from the earlier reported baseline `89e6ee5` only by `28fac97` (a log statement in `MiningOccurrenceDispatcher`). No secrets, tokens or passwords appear in this report; test accounts are synthetic and local.

## 2. The native Linux runner and how to use it

`scripts/verify-native-linux.sh --preflight` checks, without installing or changing anything:

| Check | Native rule |
|---|---|
| operating system | `uname -s` = `Linux` |
| kernel and CPU are native | rejects kernels named `linuxkit`/`microsoft`/`WSL` (Docker Desktop or WSL virtual machines) and an x86_64 or aarch64 userland whose `/proc/cpuinfo` shows no x86/Arm CPU (instruction emulation) |
| user | not root (`SpmfRuntimeTest` and the launcher tests assume a non-root service account) |
| source | clean work tree and a known commit, so the evidence names one commit |
| runtimes | Java 17 for the Backend; Java 25+ at `PASPM_MINING_JAVA_EXECUTABLE` for the SPMF child |
| SPMF | `PASPM_MINING_SPMF_JAR` must hash to the pinned SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a` (any other file is refused) |
| parent-death launcher | `python3` with `ctypes` reaching `prctl` |
| database | a **local** `jdbc:mysql://127.0.0.1:<port>/` MySQL **8.4** server (`PASPM_TEST_MYSQL_SERVER`, user/password in `PASPM_TEST_MYSQL_USER`/`PASPM_TEST_MYSQL_PASSWORD`, never printed) whose account may `CREATE DATABASE` and `CREATE TRIGGER`; tests only create new disposable schemas |

`--run [--natural-lease] [--legacy-jar PATH]` then runs, with Maven, `MiningMultiJvmMySqlTest`, `MiningSchedulerMultiJvmMySqlTest`, `MiningSchedulerUpgradeCompatibilityMySqlTest`, `MiningScheduledActivationE2EMySqlTest`, `MiningScheduledDatabaseOutageMySqlTest`, `MiningMigrationMySqlTest`, `LinuxParentDeathLauncherTest`, `SpmfRuntimeTest`, `SpmfMiningEngineTest`, `MiningDispatchLeaseMySqlTest`, `MiningScheduleReservationMySqlTest`, `MiningMySqlIntegrationTest`, and writes `preflight.json`, `result.json`, `multijvm-evidence.txt`, `maven.log` and `surefire/`. The verdict `NATIVE_LINUX_VERIFIED` is emitted only when the preflight proved a native kernel **and** Maven exited 0 with no failure or error; skipped tests are listed per class and are never counted as passes. `--natural-lease` adds the two tests that wait for real 300 s lease expiry (about 14 minutes). `--legacy-jar` adds the old-executable characterisation (jar built from `origin/dev`).

Measured on this machine:

```
macOS host:           FAIL  operating system is Linux            -> NATIVE_LINUX_NOT_RUN
Docker linux/amd64:   FAIL  kernel and CPU are native            -> "kernel '6.10.14-linuxkit' belongs to a Docker Desktop / WSL
                                                                    virtual machine; x86_64 userland without an x86 CPU in /proc/cpuinfo:
                                                                    instruction emulation"   -> NATIVE_LINUX_NOT_RUN
```

Dependencies for the approved host: Linux x86-64 (or arm64) kernel on bare metal or a VM that presents a real CPU; a non-root user; JDK 17 and JDK 25 (not the Backend's JVM); Maven; `python3`; `git`; MySQL 8.4; the approved SPMF 2.67a jar; the exact commit checked out clean; `mysql` client optional (only to print the server version in the preflight).

## 3. What was run, where, on which commit

| Environment | Commit (source tree) | Result |
|---|---|---|
| macOS 26.5 arm64, JDK 17.0.21 Backend, JDK 25 SPMF child, MySQL 8.4.13 in Docker | production code as at `28fac97`; tests as of the commits listed in the readiness report | multi-instance and crash suites: see 4–6 |
| Docker Desktop Linux 6.10.14-linuxkit, **linux/amd64 emulated, uid 10001**, Ubuntu 24.04, OpenJDK 17.0.20 / 25.0.1, MySQL 8.4.13 | `501f980` (final run) | **197 run, 0 failures, 0 errors, 2 skipped** (the two natural-lease tests, which need `-Dpaspm.test.natural-lease=true` and were run on macOS). 28 classes: both multi-JVM suites, the upgrade/compatibility class, the E2E and outage tests, `SpmfRuntimeTest` 8/8 and `LinuxParentDeathLauncherTest` 2/2 as a non-root user, the scheduler suites, `MiningIntegrationTest`, `MiningMySqlIntegrationTest`, `RecommendationMySqlIntegrationTest`. **Not native Linux.** Two earlier runs of the same environment found the test defects listed in section 7; they were fixed and this run is the clean one |

Evidence directory (not committed): `outputs/verification-evidence/sched-p2/`.

## 4. The nine crash points (real `kill -9` / SIGKILL of a real Backend JVM, MySQL 8.4, real SPMF)

Method. Deterministic freeze points, no production change: a test-only MySQL trigger takes a named lock held by the test, so the Backend blocks **inside** a statement; the test records the committed state, sends SIGKILL, lets MySQL release the dead connection (observed to do so by itself in every case: `server_noticed_disconnect=true`, otherwise the test would `KILL` it and say so), then starts or lets another JVM recover. No commit boundary is invented: snapshot creation, request creation and the occurrence link are **one** transaction, so "after the snapshot commit and before the link" does not exist as a state and is covered by the enclosing points. `MiningSchedulerMultiJvmMySqlTest`, opt-in `-Dpaspm.test.multijvm=true`.

All counts below are checked by the test after recovery: snapshots = 1, requests = 1, duplicate request keys/occurrences = 0, orphan snapshots/requests (not referenced by any occurrence) = 0.

| # | Kill point (where the Backend was blocked) | Committed before the kill | Recovery owner and fence | Final state | Result |
|---|---|---|---|---|---|
| 1 | **Before the reservation commits**: inside `INSERT INTO mining_schedule_occurrence`, schedule row locked | nothing; cursor `next_fire_at` unchanged (the fire time is not consumed) | other JVM reserves afresh, fence 1, attempt 1 | occurrence `COMPLETED`, 1 snapshot, 1 request | PASS |
| 2 | **After the reservation committed, before it is claimed**: inside the claim `UPDATE … SET status='PROCESSING'` | occurrence `PENDING`, `lease_fence=0`, no owner, no snapshot/request | other JVM claims, fence 1 (no lease existed, so no expiry wait) | `COMPLETED` | PASS |
| 3 | **During F03 snapshot preparation**: snapshot written inside the open occurrence transaction (barrier in the engine's availability check) | lease held (`PROCESSING`, fence 1, `lease_until` +300 s); the uncommitted snapshot is invisible and is rolled back by MySQL (0 snapshots after the kill) | other JVM must wait for the lease; it did **not** steal an unexpired lease (12 s observed); new fence 2, attempt 2 | `COMPLETED`, 1 snapshot | PASS — **artificial** expiry (`lease_until` moved into the past) |
| 3n | same, **natural** lease expiry | same; `lease_until` never edited | takeover only after `lease_until`; **309 s** from the kill to recovery; fence 2, attempt 2 | `COMPLETED` | PASS (macOS; `-Dpaspm.test.natural-lease=true`) |
| 4 | **After snapshot + request + link committed, worker about to start SPMF** (barrier before the engine runs) | occurrence `DISPATCHED`, request committed with worker lease fence 1 | other JVM discovers the committed work; the schedule is disabled meanwhile (D8); request fence higher than before | request and occurrence `COMPLETED`, 16 patterns | PASS |
| 5 | **Before the worker claims the committed request**: inside the claim `UPDATE mining_request … 'RUNNING'` | snapshot, request and link committed; request `PENDING`, no worker lease | other JVM claims, request fence 1 | request and occurrence `COMPLETED`, 16 patterns | PASS |
| 6 | **During real SPMF execution**: the Java 25 SPMF child is running (stopped with SIGSTOP so it is observable when the Backend dies) | request `RUNNING`, worker lease fence 1 | other JVM recovers after the worker lease expires (natural), fence 2 | request and occurrence `COMPLETED`, 2 runs, 16 patterns | PASS |
| 7 | **During occurrence finalization**: inside the reconcile `UPDATE … 'COMPLETED'` | request `COMPLETED`, 16 patterns; occurrence still `DISPATCHED` | next JVM reconciles; the occurrence lease is not touched (fence stays 1) | `COMPLETED` | PASS |
| 8 | **During retry backoff**: attempt 1 failed retryably (SPMF unavailable), occurrence `PENDING` with `next_retry_at` in the future | attempt 1 recorded (`RETRYABLE_FAILURE`, message), no snapshot | other JVM runs attempt 2 after the real 15 s backoff, no timestamp edited; fence 2 | `COMPLETED` | PASS |
| 9 | **During reconciliation by the recovering instance**: the instance that took over from #7 is itself killed inside the same `UPDATE` | as #7 | a third JVM reconciles | `COMPLETED` | PASS (same step as #7, killed twice) |

Expiry classification: natural — 3n (309 s), the stale-owner test below (385 s), 6 (worker lease), 8 (15 s backoff); artificial — 3, and the earlier `scheduler_crash_in_transaction` run in the same class. The takeover decisions never depended on an edited timestamp except where marked.

### Stale owner and fencing

`staleOwnerThatWakesUpAfterItsLeaseExpiredNaturallyCannotCommit…` (`-Dpaspm.test.natural-lease=true`): the owner is stopped (SIGSTOP) inside its occurrence transaction while holding the occurrence row lock; the lease runs out by itself. Observed: the other instance could **not** take the occurrence while the owner held its lock (fence still 1, no request, 385 s), the owner was resumed, its commit was **refused by the fence** (`Occurrence lease expired before dispatch commit`) and rolled back, the other instance then completed it with fence 2 and attempt 2. Result: exactly 1 snapshot, 1 request, 0 duplicates, 0 orphans. PASS.

A frozen owner that never resumes stalls its own occurrence until the process is killed or MySQL closes its connection (finding R3 in the [readiness report](F05_D8_D9_D10_RELEASE_READINESS_2026-11-02.md)).

## 5. Multi-instance Scheduler

| Property | Evidence | Result |
|---|---|---|
| Concurrent polling creates no duplicate logical occurrence | two JVMs, one schedule three weeks overdue: 4 occurrences (3 `SKIPPED` + 1 `COMPLETED`), 12 s of further polling adds nothing | PASS |
| Only one valid lease owner at a time | claim `UPDATE` is conditional on status/expiry; owner, fence and attempt recorded; takeover tests above | PASS |
| Stale owners cannot commit | fenced `finishDispatch` plus the owner's row lock; stale-owner test | PASS |
| Each due occurrence yields at most one Mining Request | unique `(store_id, request_key)` shared by occurrence and request; `requests=1` in every case | PASS |
| Latest-only catch-up; older fire times `SKIPPED`; `SKIPPED` rows own no snapshot/request/run | same test: `SKIPPED` rows have `snapshot_id`, `request_id` NULL; 1 snapshot, 1 request, 2 runs | PASS |
| Database consistent after restart | integrity check after every recovery (zero duplicates, zero orphans); two Backends started together on an empty schema and restarted: one principal, V1–V19 once | PASS |
| Real authenticated API for schedule creation and activation | tokens from the real login flow, `POST` then `PUT enabled=true` over HTTP against each Backend; default-off verified separately in a real process (activation 503, an overdue enabled row is never run) | PASS |

## 6. SPMF child cleanup and process ownership

| Property | macOS (development platform) | Linux, emulated, uid 10001 |
|---|---|---|
| Backend A spawns SPMF child A (real Java 25, `HUI-Miner`) | pid recorded, parent = Backend A, same uid as the Backend (`spmf_uid == backend_uid`) | same; the numeric uid is recorded from `/proc/<pid>/status` (`10001`, not root) |
| SIGKILL of Backend A terminates child A | **no**: `orphan_alive_after_backend_sigkill=true`, `parent_death_enforced_by_kernel=false`. macOS has no parent-death facility; the test kills the orphan itself and the Backend logs a warning. Development use only | **yes**: `orphan_alive_after_backend_sigkill=false`, `parent_death_enforced_by_kernel=true` (kernel `PR_SET_PDEATHSIG` set by the Python launcher) |
| Child B (independent Backend B) is unaffected | `real_spmf_crash` ×2: `other_spmf_survived=true` | same ×2 |
| Backend B recovers the unfinished request | request fence 1 → 2, 16 patterns (scheduled) / 8 (manual) | same |
| No stale child persists and consumes CPU | the orphan is killed by the test (macOS); none remains (Linux) | none remains |
| A stale worker cannot overwrite a newer fenced result | `lease_loss`: child terminated, `stale_patterns=0`; `stale`: repository rejects the stale write (`Mining lease is…`) | same |
| The guarantee is not Docker init reaping | – | the test counts a zombie (state `Z`) as *not running*, so the assertion is that the **kernel killed** the child while it was stopped and its parent was dead. Container `--init` only reaps the corpse afterwards (`reaped_by_container_init` is reported separately and is not what is asserted) |

Launcher start-up race. A launcher that is stopped **before** it registers `PR_SET_PDEATHSIG` cannot be cleaned up by the kernel. This was observed once, by accident, in the first Linux run of crash point 6: the test stopped the pid it had just read from the log while it was still the Python launcher, and that stopped launcher outlived the dead Backend for more than 15 seconds. That is the documented limit ("a launcher indefinitely stopped before registration"), not a defect in the guarantee; the test now waits for the real `HUI-Miner` java process, as the older worker test already did. The controlled variant of this race (a test wrapper holding the launcher before registration, release after the parent died, launcher exits without starting SPMF) was recorded in the 2026-11-01 evidence and was **not** re-run in this task.

## 7. Defects found in the new tests while running them on Linux (not in production)

| Test defect | Effect | Fix |
|---|---|---|
| crash point 6 stopped the launcher instead of the SPMF java | false failure on Linux (see launcher race above) | wait for the `HUI-Miner` process |
| upgrade test reused a pooled HTTP connection that the server had closed while another slow Backend was starting | `header parser received no bytes` on the emulated Linux | fresh client once a Backend is up |
| two simultaneous Backends both ran the bootstrap-administrator check-then-insert | one exited with `DuplicateKeyException`; **pre-existing production behaviour in `Bootstrap`, not part of F05**, not changed here | only one Backend gets the bootstrap credentials in that test |

The last one is a real, minor, out-of-scope observation: two instances started together on a database that has no bootstrap administrator yet, with the bootstrap variables set on both, race, and the loser exits (a restart succeeds). Recommend a separate fix; it is unrelated to scheduling and the F05 acceptance does not depend on it.

## 8. Not covered

- Native Linux (this report's verdict). Run `scripts/verify-native-linux.sh --run --natural-lease --legacy-jar <origin/dev jar>` on the approved host and attach `result.json`.
- GitHub CI; these suites are opt-in and outside the CI groups.
- A MySQL server restart or a long network partition (only TCP drops to a proxy were tested, earlier).
- Clock jumps and NTP step changes.
