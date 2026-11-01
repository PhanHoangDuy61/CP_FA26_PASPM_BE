package vn.edu.fpt.paspm.mining;

import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/** Database state is the dispatch authority; the local queue is only a wake-up hint. */
@Component
public class MiningWorker {
    private static final Logger log=LoggerFactory.getLogger(MiningWorker.class);
    private static final int LEASE_SECONDS=30;
    private final MiningRepository repository;
    private final MiningEngine engine;
    private final MiningDispatchLease leases;
    private final String owner=UUID.randomUUID().toString();
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService poller=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"paspm-mining-dispatch");t.setDaemon(true);return t;});
    private final ScheduledExecutorService heartbeats=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"paspm-mining-lease");t.setDaemon(true);return t;});
    private final Set<String> queued=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean started=new AtomicBoolean();
    private volatile boolean ready;
    private final boolean migrationsEnabled;
    public MiningWorker(MiningRepository repository,MiningEngine engine,MiningDispatchLease leases,@Value("${spring.flyway.enabled:true}") boolean migrationsEnabled){
        this.migrationsEnabled=migrationsEnabled;this.repository=repository;this.engine=engine;this.leases=leases;
        this.executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),task->{var t=new Thread(task,"paspm-mining");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover(){
        if(!migrationsEnabled||!started.compareAndSet(false,true))return;
        ready=true;poller.scheduleWithFixedDelay(this::poll,0,1,TimeUnit.SECONDS);
    }
    private void poll(){
        if(!ready)return;
        try{for(var candidate:repository.dispatchCandidates())enqueue(candidate.storeId(),candidate.requestId());}
        catch(Exception e){log.warn("Mining dispatch poll failed; it will retry",e);}
    }
    void enqueue(int store,long id){
        if(!ready)return;
        String key=store+":"+id;
        if(!queued.add(key))return;
        try{executor.execute(()->{try{execute(store,id);}finally{queued.remove(key);}});}
        catch(RejectedExecutionException e){queued.remove(key);log.debug("MINING_QUEUE_REJECTED store={} request={}",store,id);/* Durable PENDING row is retried by the poller. */}
    }
    void execute(int store,long id){
        final MiningDispatchLease.Claim claim;
        try{var acquired=leases.claim(store,id,owner,LEASE_SECONDS);if(acquired.isEmpty())return;claim=acquired.get();}
        catch(Exception e){log.warn("Mining lease claim failed; dispatch will retry",e);return;}
        Thread worker=Thread.currentThread();var active=new AtomicBoolean(true);
        var heartbeat=heartbeats.scheduleWithFixedDelay(()->{
            try{if(active.get()&&!leases.renew(claim,LEASE_SECONDS)&&active.compareAndSet(true,false))worker.interrupt();}
            catch(Exception e){if(active.compareAndSet(true,false))worker.interrupt();log.warn("Mining lease renewal failed",e);}
        },5,5,TimeUnit.SECONDS);
        try{
            repository.resetAbandonedRuns(claim);
            var request=repository.find(store,id);
            for(var run:repository.runs(store,id)){
                if(!repository.claimRun(claim,run.runId()))continue;
                MiningEngine.Output output;
                try{
                    output=engine.execute(run.algorithm(),request.input(),request.view().minUtilSpmf(),new MiningEngine.ExecutionIdentity(store,id,run.runId(),claim.owner(),claim.fence()));
                }catch(SpmfMiningEngine.ExecutionFailure e){
                    if(!active.get()||Thread.currentThread().isInterrupted())throw e;
                    repository.failRun(claim,run.runId(),e.code(),e.getMessage());
                    continue;
                }catch(Exception e){
                    if(!active.get()||Thread.currentThread().isInterrupted())throw e;
                    repository.failRun(claim,run.runId(),"EXECUTION_FAILED","Mining execution or result persistence failed");
                    continue;
                }
                repository.complete(claim,run.runId(),request.input(),output);
            }
            var runs=repository.runs(store,id);
            boolean success=runs.stream().allMatch(r->r.status().equals("COMPLETED"));
            String comparison=runs.size()==1?"NOT_REQUESTED":success?(repository.canonical(store,runs.get(0).runId()).equals(repository.canonical(store,runs.get(1).runId()))?"MATCH":"FAILED"):"BLOCKED";
            leases.terminal(claim,success&&!comparison.equals("FAILED")?"COMPLETED":"FAILED",comparison);
        }catch(Exception e){log.warn("Mining request could not finish under its lease; it remains recoverable",e);}
        finally{active.set(false);heartbeat.cancel(false);}
    }
    @PreDestroy public void close(){ready=false;poller.shutdownNow();executor.shutdownNow();heartbeats.shutdownNow();}
}
