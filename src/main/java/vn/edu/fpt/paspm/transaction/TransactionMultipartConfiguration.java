package vn.edu.fpt.paspm.transaction;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionMultipartConfiguration {
    @Bean
    MultipartConfigElement f04MultipartConfigElement(TransactionImportProperties properties) {
        long maxFileBytes = properties.maxFileBytes();
        long maxRequestBytes = Math.addExact(maxFileBytes, 1_048_576L);
        return new MultipartConfigElement("", maxFileBytes, maxRequestBytes, 0);
    }
}
