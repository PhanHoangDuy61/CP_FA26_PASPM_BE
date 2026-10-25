package vn.edu.fpt.paspm.common.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** Boot supplies one JpaTransactionManager, exposing its connection to existing JDBC repositories. */
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:persistence.properties")
public class PersistenceConfiguration {
}
