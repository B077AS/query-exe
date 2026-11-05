package com.queryexe.monitor;

import java.util.List;


/** The shape of what an engine reports. The page draws one chart per {@link Chart} with a line per metric that points at it. */
@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class MonitorSpec {

    private List<Chart> charts;
    private List<Metric> metrics;

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Chart {
        private String id;
        private String title;
        /** How values are shown: "" (a count), "/s", "B/s" or "s". */
        private String unit;
    }

    public enum Kind {
        /** The value is what it is right now. */
        GAUGE,
        /** The value is a running total; the chart shows how fast it grows, per second. */
        RATE
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Metric {
        private String key;
        private String label;
        /** The chart it is drawn on, or null when it is only shown as a number. */
        private String chart;
        private Kind kind;
        /** Shown as a big number at the top of the overview. */
        private boolean headline;
        /** Unit of the number when it is not drawn on a chart. */
        private String unit;
    }

    // ---- small builders so the engines read as tables ----

    public static Chart chart(String id, String title, String unit) {
        return new Chart(id, title, unit);
    }

    public static Metric gauge(String key, String label, String chart, boolean headline) {
        return new Metric(key, label, chart, Kind.GAUGE, headline, "");
    }

    public static Metric rate(String key, String label, String chart) {
        return new Metric(key, label, chart, Kind.RATE, false, "");
    }

    /** A number with no chart (a limit, a total since start). */
    public static Metric note(String key, String label, String unit) {
        return new Metric(key, label, null, Kind.GAUGE, true, unit);
    }
}
