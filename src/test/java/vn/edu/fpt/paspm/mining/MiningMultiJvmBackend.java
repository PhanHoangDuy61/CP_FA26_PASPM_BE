package vn.edu.fpt.paspm.mining;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import vn.edu.fpt.paspm.PaspmApplication;

/** Test-classpath-only Backend with filesystem barriers around the real SPMF engine and enqueue call. */
public class MiningMultiJvmBackend {
    public static void main(String[] args) {
        SpringApplication app=new SpringApplication(PaspmApplication.class,Barriers.class);
        app.run(args);
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class Barriers {
        private final Path dir=Path.of(System.getenv("PASPM_TEST_BARRIER_DIR"));
        @Bean @Primary MiningEngine controlledEngine(SpmfMiningEngine real) {
            return new MiningEngine() {
                public void requireAvailable(){real.requireAvailable();}
                public Output execute(Algorithm algorithm,MiningInputBuilder.Dataset input,int minimum) throws Exception {
                    return controlled(algorithm,input,minimum,null);
                }
                public Output execute(Algorithm algorithm,MiningInputBuilder.Dataset input,int minimum,ExecutionIdentity identity) throws Exception {
                    return controlled(algorithm,input,minimum,identity);
                }
                private Output controlled(Algorithm algorithm,MiningInputBuilder.Dataset input,int minimum,ExecutionIdentity identity) throws Exception {
                    if(Files.exists(dir.resolve("hold-before-spmf"))) {
                        Files.writeString(dir.resolve("before-spmf"),algorithm.name());
                        while(Files.exists(dir.resolve("hold-before-spmf")))Thread.sleep(10);
                    }
                    Output output=identity==null?real.execute(algorithm,input,minimum):real.execute(algorithm,input,minimum,identity);
                    if(Files.exists(dir.resolve("hold-output"))) {
                        Files.writeString(dir.resolve("output-held"),algorithm.name());
                        while(Files.exists(dir.resolve("hold-output"))) {
                            try { Thread.sleep(25); }
                            catch(InterruptedException e) { if(!Files.exists(dir.resolve("ignore-interrupt")))throw e; }
                        }
                    }
                    return output;
                }
            };
        }
        @Bean(name="miningWorker") MiningWorker controlledWorker(MiningRepository repository,MiningEngine engine,MiningDispatchLease leases) {
            return new MiningWorker(repository,engine,leases,true) {
                @Override void enqueue(int store,long id) {
                    if(!Thread.currentThread().getName().equals("paspm-mining-dispatch") && Files.exists(dir.resolve("hold-enqueue"))) {
                        try {
                            Files.writeString(dir.resolve("enqueue-held"),Long.toString(id));
                            while(Files.exists(dir.resolve("hold-enqueue")))Thread.sleep(10);
                        } catch(Exception e){Thread.currentThread().interrupt();return;}
                    }
                    super.enqueue(store,id);
                }
            };
        }
    }
}
