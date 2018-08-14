package org.pragmaticminds.crunch.api.values;

import lombok.*;
import org.pragmaticminds.crunch.api.values.dates.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class for Transporting "UntypedValues" e.g. over Kafka.
 *
 * @author julian
 * Created by julian on 23.10.17
 */
@Data
@EqualsAndHashCode
@NoArgsConstructor
public class UntypedValues implements ValueEvent {

    private String source;
    private long timestamp;
    private String prefix;
    private HashMap<String, Object> values;
    
    public UntypedValues(
        String source, long timestamp, String prefix, Map<String, Object> values
    ) {
        this.source = source;
        this.timestamp = timestamp;
        this.prefix = prefix;
        this.values = values == null ? null : new HashMap<>(values);
    }
    
    /**
     * converts UnTyped-Values to typed values
     * to differ between different sources (PLC-devices such as S71500, S7300, ...) a prefix is added to each Channel-Name for indication that those channels are not from main SPS
     *
     * @return regarding TypedValues
     */
    public TypedValues toTypedValues() {
        Map<String, Value> valueMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String channelName = entry.getKey();
            if (!prefix.isEmpty()) {
                channelName = prefix + "_" + entry.getKey();
            }
            valueMap.put(channelName, Value.of(entry.getValue()));
        }
        return new TypedValues(source, timestamp, valueMap);
    }

    /**
     * Returns a new {@link UntypedValues} Object which contains only values that are in the channels-set
     * @param channels Set for the channels to keep
     * @return New {@link UntypedValues} which contains the "intersection" with the channels Set
     */
    public UntypedValues filterChannels(Set<String> channels) {
        Map<String, Object> filteredValues = values.entrySet().stream()
                .filter(entry -> channels.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new UntypedValues(source, timestamp, prefix, new HashMap<>(filteredValues));
    }

    /**
     * Returns true if there are no values in the Values-Map.
     * @return
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    public String getPrefix() {
        return this.prefix;
    }

    public Map<String, Object> getValues() {
        return values;
    }
    
    /**
     * setter makes {@link HashMap} from {@link Map}
     * @param values to be saved as {@link HashMap}
     */
    public void setValues(Map<String, Object> values) {
        this.values = values == null ? null : new HashMap<>(values);
    }
    
    /**
     * Creates a builder for this class
     * @return a builder for this class
     */
    public static Builder builder() { return new Builder(); }
    
    /**
     * Builder for this class
     */
    public static final class Builder {
        private String              source;
        private long                timestamp;
        private String              prefix;
        private Map<String, Object> values;
        
        private Builder() {}
        
        
        public Builder source(String source) {
            this.source = source;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }
        
        public Builder values(Map<String, Object> values) {
            this.values = values;
            return this;
        }
        
        public Builder but() {
            return builder().source(source)
                .timestamp(timestamp)
                .prefix(prefix)
                .values(values);
        }
        
        public UntypedValues build() {
            UntypedValues untypedValues = new UntypedValues();
            untypedValues.setSource(source);
            untypedValues.setTimestamp(timestamp);
            untypedValues.setPrefix(prefix);
            untypedValues.setValues(values);
            return untypedValues;
        }
    }
}