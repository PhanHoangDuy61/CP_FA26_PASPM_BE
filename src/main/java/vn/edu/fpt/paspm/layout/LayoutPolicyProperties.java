package vn.edu.fpt.paspm.layout;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "paspm.layout")
public class LayoutPolicyProperties {
    private Map<String, Integer> maxBlocksByZone = new LinkedHashMap<>();
    private Map<String, Integer> maxProductsByZone = new LinkedHashMap<>();

    public Map<String, Integer> getMaxBlocksByZone() { return maxBlocksByZone; }
    public void setMaxBlocksByZone(Map<String, Integer> value) {
        maxBlocksByZone = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    public Map<String, Integer> getMaxProductsByZone() { return maxProductsByZone; }
    public void setMaxProductsByZone(Map<String, Integer> value) {
        maxProductsByZone = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
}
