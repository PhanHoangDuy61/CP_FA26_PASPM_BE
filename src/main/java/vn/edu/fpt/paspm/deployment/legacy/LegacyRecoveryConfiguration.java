package vn.edu.fpt.paspm.deployment.legacy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Spring Boot discovers Callback beans and registers them with its Flyway instance. */
@Configuration(proxyBeanMethods=false)
public class LegacyRecoveryConfiguration {
 @Bean LegacyRecoveryCallback legacyRecoveryCallback(Environment env){return new LegacyRecoveryCallback(Boolean.parseBoolean(env.getProperty("paspm.legacy-recovery.enabled","false")),env.getProperty("paspm.legacy-recovery.manifest"),env.getProperty("paspm.legacy-recovery.restore-url"),env.getProperty("paspm.legacy-recovery.restore-user"),env.getProperty("paspm.legacy-recovery.restore-password"));}
}
