package vn.edu.fpt.paspm.common.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MediaConfiguration {
    /** Root from {@code paspm.media.root} ({@code PASPM_MEDIA_ROOT}); blank means {@code ~/.paspm/media}. */
    @Bean
    public MediaStorage mediaStorage(@Value("${paspm.media.root:}") String root) {
        return new LocalMediaStorage(root);
    }
}
