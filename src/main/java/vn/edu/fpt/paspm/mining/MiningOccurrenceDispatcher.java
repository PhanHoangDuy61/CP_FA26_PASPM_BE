package vn.edu.fpt.paspm.mining;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;

/** Disabled by default until scheduler safety acceptance is complete. */
@Component
public class MiningOccurrenceDispatcher {
    private static final Logger log=LoggerFactory.getLogger(MiningOccurrenceDispatcher.class);
    private final JdbcTemplate jdbc;private final MiningScheduleReservation reservation;private final MiningOccurrenceLease leases;private final MiningOccurrenceProcessor processor;
    private final boolean enabled;private final String owner=UUID.randomUUID().toString();private final AtomicBoolean started=new AtomicBoolean();
    private final ScheduledExecutorService poller=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"paspm-schedule-dispatch");t.setDaemon(true);return t;});
    public MiningOccurrenceDispatcher(JdbcTemplate jdbc,MiningScheduleReservation reservation,MiningOccurrenceLease leases,MiningOccurrenceProcessor processor,@Value("${paspm.mining.schedule-dispatch-enabled:false}") boolean enabled){this.jdbc=jdbc;this.reservation=reservation;this.leases=leases;this.processor=processor;this.enabled=enabled;}
    @EventListener(ApplicationReadyEvent.class) public void start(){if(enabled&&started.compareAndSet(false,true))poller.scheduleWithFixedDelay(this::poll,0,5,TimeUnit.SECONDS);}
    void poll(){
        try {for(int i=0;i<5;i++){var outcome=reservation.reserve(Instant.now());if(!outcome.progressed())break;logReservation(outcome);}}
        catch(Exception e){log.warn("Scheduled occurrence reservation failed; next poll will retry",e);}
        try {
            jdbc.update("UPDATE mining_schedule_occurrence SET status='FAILED',failure_code='RETRY_EXHAUSTED',failure_message='Lease expired on the final attempt',lease_owner=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE status='PROCESSING' AND lease_owner IS NOT NULL AND lease_until<=CURRENT_TIMESTAMP AND attempt_count>=?",MiningOccurrenceLease.MAX_ATTEMPTS);
            var candidates=jdbc.queryForList("SELECT occurrence_id FROM mining_schedule_occurrence WHERE attempt_count<? AND ((status='PENDING' AND (next_retry_at IS NULL OR next_retry_at<=CURRENT_TIMESTAMP)) OR (status='PROCESSING' AND lease_owner IS NOT NULL AND lease_until<=CURRENT_TIMESTAMP)) ORDER BY occurrence_id LIMIT 5",Long.class,MiningOccurrenceLease.MAX_ATTEMPTS);
            for(long id:candidates){
                var claim=leases.claim(id,owner);if(claim.isEmpty())continue;
                try{processor.process(claim.get());}
                catch(Exception e){boolean retry=e instanceof TransientDataAccessException || e instanceof ApiException api && api.status()>=500;String code=retry?"RETRYABLE_FAILURE":e instanceof ApiException api?"HTTP_"+api.status():"PROCESSING_FAILED";leases.fail(claim.get(),code,retry,visibleMessage(e));log.warn("Scheduled occurrence {} failed; retryable={}",id,retry,e);}
            }
        } catch(Exception e){log.warn("Scheduled occurrence dispatch failed; next poll will retry",e);}
        try{reconcile();}catch(Exception e){log.warn("Scheduled occurrence reconciliation failed; next poll will retry",e);}
    }
    /** Catch-up after downtime must not be silent: the skipped fire times are in the history API, and the operator log says that it happened (D6). */
    static void logReservation(MiningScheduleReservation.Outcome outcome){
        if(outcome.skipped()>0)log.warn("Scheduled mining skipped {} older missed fire time(s) (latest-only catch-up){}",outcome.skipped(),outcome.occurrenceId()==null?"; the backlog continues on the next poll":" and reserved occurrence "+outcome.occurrenceId());
        else if(outcome.occurrenceId()!=null)log.info("Scheduled mining reserved occurrence {}",outcome.occurrenceId());
    }
    /** Administrator-visible cause: curated API messages as written, anything else only as a class name (details stay in the server log). */
    static String visibleMessage(Exception e){
        if(e instanceof ApiException api)return api.getMessage();
        if(e instanceof TransientDataAccessException)return "Temporary database error";
        return "Unexpected error ("+e.getClass().getSimpleName()+"); see the server log";
    }
    void reconcile(){
        var ids=jdbc.queryForList("SELECT o.occurrence_id FROM mining_schedule_occurrence o JOIN mining_request r ON r.request_id=o.request_id AND r.store_id=o.store_id WHERE o.status='DISPATCHED' AND r.status IN ('COMPLETED','FAILED') ORDER BY o.occurrence_id LIMIT 100",Long.class);
        for(long id:ids)jdbc.update("UPDATE mining_schedule_occurrence SET status=(SELECT CASE WHEN r.status='COMPLETED' THEN 'COMPLETED' ELSE 'FAILED' END FROM mining_request r WHERE r.request_id=mining_schedule_occurrence.request_id AND r.store_id=mining_schedule_occurrence.store_id),failure_code=(SELECT CASE WHEN r.status='FAILED' THEN 'MINING_FAILED' ELSE NULL END FROM mining_request r WHERE r.request_id=mining_schedule_occurrence.request_id AND r.store_id=mining_schedule_occurrence.store_id),failure_message=(SELECT CASE WHEN r.status='FAILED' THEN COALESCE((SELECT x.failure_message FROM mining_run x WHERE x.request_id=r.request_id AND x.store_id=r.store_id AND x.status='FAILED' ORDER BY x.run_id LIMIT 1),'Mining request failed') ELSE NULL END FROM mining_request r WHERE r.request_id=mining_schedule_occurrence.request_id AND r.store_id=mining_schedule_occurrence.store_id),updated_at=CURRENT_TIMESTAMP WHERE occurrence_id=? AND status='DISPATCHED' AND EXISTS (SELECT 1 FROM mining_request r WHERE r.request_id=mining_schedule_occurrence.request_id AND r.store_id=mining_schedule_occurrence.store_id AND r.status IN ('COMPLETED','FAILED'))",id);
    }
    @PreDestroy public void close(){poller.shutdownNow();}
}
