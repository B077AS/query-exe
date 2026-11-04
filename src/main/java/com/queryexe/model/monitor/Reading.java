package com.queryexe.model.monitor;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Value;

/** One look at the server: raw values by metric key (gauges as they are, counters as running totals). */
@Value
public class Reading {

    Map<String, Double> values;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Double> values = new LinkedHashMap<>();

        public Builder put(String key, Number value) {
            if (value != null) {
                values.put(key, value.doubleValue());
            }
            return this;
        }

        public Reading build() {
            return new Reading(values);
        }
    }
}
