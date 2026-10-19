package vn.edu.fpt.paspm.mining;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import static vn.edu.fpt.paspm.mining.MiningContracts.*;

/** One worker per deployment. Deploy a single backend instance until a distributed lease is added. */
@Component
public class MiningWorker {
    private final MiningRepository repository;
    private final MiningEngine engine;
    private final ThreadPoolExecutor executor;
    private volatile boolean ready;
    private final boolean migrationsEnabled;
    public MiningWorker(MiningRepository repository,MiningEngine engine,@Value("${spring.flyway.enabled:true}") boolean migrationsEnabled){
        this.migrationsEnabled=migrationsEnabled;
        this.repository=repository;this.engine=engine;
        this.executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),task->{var t=new Thread(task,"paspm-mining");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover(){if(!migrationsEnabled)return;repository.recoverInterrupted();ready=true;}
    void enqueue(int store,long id){
        try{if(!ready)throw new RejectedExecutionException();executor.execute(()->execute(store,id));}
        catch(RejectedExecutionException e){for(var run:repository.runs(store,id))repository.failRun(store,run.runId(),"QUEUE_FULL","Mining capacity unavailable; submit a new requestKey to retry");repository.finish(store,id,"FAILED","BLOCKED");}
    }
    void execute(int store,long id){
        if(!repository.claimRequest(store,id))return;
        try{
            var request=repository.find(store,id);
            for(var run:repository.runs(store,id)){
                if(!repository.claimRun(store,run.runId()))continue;
                try{
                    var output=engine.execute(run.algorithm(),request.input(),request.view().minUtilSpmf());
                    repository.complete(store,run.runId(),request.input(),output);
                }catch(SpmfMiningEngine.ExecutionFailure e){repository.failRun(store,run.runId(),e.code(),e.getMessage());}
                catch(Exception e){repository.failRun(store,run.runId(),"EXECUTION_FAILED","Mining execution or result persistence failed");}
            }
            var runs=repository.runs(store,id);
            boolean success=runs.stream().allMatch(r->r.status().equals("COMPLETED"));
            String comparison=runs.size()==1?"NOT_REQUESTED":success?(repository.canonical(store,runs.get(0).runId()).equals(repository.canonical(store,runs.get(1).runId()))?"MATCH":"FAILED"):"BLOCKED";
            repository.finish(store,id,success&&!comparison.equals("FAILED")?"COMPLETED":"FAILED",comparison);
        }catch(Exception e){
            for(var run:repository.runs(store,id))repository.failRun(store,run.runId(),"EXECUTION_FAILED","Mining request interrupted before finalization");
            repository.finish(store,id,"FAILED","BLOCKED");
        }
    }
    @PreDestroy public void close(){ready=false;executor.shutdownNow();}
}
