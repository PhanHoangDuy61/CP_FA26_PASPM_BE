package vn.edu.fpt.paspm.transaction.importing;

import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionImportWorker {
    private static final Logger log = LoggerFactory.getLogger(TransactionImportWorker.class);
    private static final int LEASE_SECONDS = 60;

    private final TransactionImportRepository repository;
    private final TransactionImportService service;
    private final TransactionImportLease leases;
    private final String owner = UUID.randomUUID().toString();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "paspm-transaction-import");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "paspm-transaction-import-dispatch");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "paspm-transaction-import-lease");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<String> queued = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean started = new AtomicBoolean();
    private final boolean migrationsEnabled;
    private volatile boolean ready;

    public TransactionImportWorker(TransactionImportRepository repository, TransactionImportService service,
            TransactionImportLease leases, @Value("${spring.flyway.enabled:true}") boolean migrationsEnabled) {
        this.repository = repository;
        this.service = service;
        this.leases = leases;
        this.migrationsEnabled = migrationsEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        if (!migrationsEnabled || !started.compareAndSet(false, true)) return;
        ready = true;
        poller.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS);
    }

    private void poll() {
        if (!ready) return;
        try {
            for (TransactionImportRepository.DispatchCandidate candidate : repository.dispatchCandidates()) {
                enqueue(candidate.storeId(), candidate.batchId());
            }
        } catch (Exception exception) {
            log.warn("F04 import dispatch poll failed; it will retry", exception);
        }
    }

    private void enqueue(int storeId, int batchId) {
        if (!ready) return;
        String key = storeId + ":" + batchId;
        if (!queued.add(key)) return;
        try {
            executor.execute(() -> {
                try {
                    execute(storeId, batchId);
                } finally {
                    queued.remove(key);
                }
            });
        } catch (RejectedExecutionException exception) {
            queued.remove(key);
            log.debug("F04_IMPORT_QUEUE_REJECTED store={} batch={}", storeId, batchId);
        }
    }

    private void execute(int storeId, int batchId) {
        final TransactionImportLease.Claim claim;
        try {
            var acquired = leases.claim(storeId, batchId, owner, LEASE_SECONDS);
            if (acquired.isEmpty()) return;
            claim = acquired.get();
        } catch (Exception exception) {
            log.warn("F04 import lease claim failed; dispatch will retry", exception);
            return;
        }

        Thread worker = Thread.currentThread();
        AtomicBoolean active = new AtomicBoolean(true);
        var heartbeat = heartbeats.scheduleWithFixedDelay(() -> {
            try {
                if (active.get() && !leases.renew(claim, LEASE_SECONDS) && active.compareAndSet(true, false)) {
                    worker.interrupt();
                }
            } catch (Exception exception) {
                if (active.compareAndSet(true, false)) worker.interrupt();
                log.warn("F04 import lease renewal failed", exception);
            }
        }, 10, 10, TimeUnit.SECONDS);

        try {
            service.processBatch(claim);
        } catch (Exception exception) {
            if (Thread.currentThread().isInterrupted()) {
                leases.releaseForRetry(claim);
                return;
            }
            log.error("F04 import batch failed store={} batch={}", storeId, batchId, exception);
            try {
                leases.terminal(claim, () -> repository.failBatch(claim,
                        "Import processing failed; submit a new import with a new idempotency key to retry"));
            } catch (Exception persistenceException) {
                log.error("F04 import failure state could not be persisted store={} batch={}",
                        storeId, batchId, persistenceException);
            }
        } finally {
            active.set(false);
            heartbeat.cancel(false);
        }
    }

    @PreDestroy
    public void close() {
        ready = false;
        poller.shutdownNow();
        executor.shutdownNow();
        heartbeats.shutdownNow();
    }
}
