package io.github.b077as.queryexe.query.explain;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** The plan of one statement: the steps as a tree (several roots for engines that list them flat) and the engine's own text. */
@Data
public class Plan {

    private String engine;
    private String sql;
    /** True when the statement was really run, so actual rows and times are there. */
    private boolean analyzed;
    /** Whether this engine can run the statement to measure it. */
    private boolean canAnalyze;
    private List<PlanNode> roots = new ArrayList<>();
    /** The plan as the engine printed it (JSON, XML, text), for the "raw" view. */
    private String raw;
    /** Planning and execution time in ms, where the engine says so. */
    private Double planningMs;
    private Double executionMs;
}
