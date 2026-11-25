package io.github.b077as.queryexe.query.explain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * One step of an execution plan, in the same shape for every engine. Numbers an engine does not report stay null.
 * {@code cost} is cumulative (this step and everything below it), as the engines themselves report it.
 */
@Data
public class PlanNode {

    /** What the step does: "Seq Scan", "Hash Join", "SCAN", "Clustered Index Seek"... */
    private String op;
    /** The table or index the step reads, when it reads one. */
    private String relation;
    /** The one-line description: the condition, the index used... */
    private String detail;
    private Double cost;
    private Double rows;
    private Double actualRows;
    /** Wall time of the step including its children, in milliseconds, when the query was really run. */
    private Double actualMs;
    private Double loops;
    /** Everything else the engine said about the step, for the detail pane. */
    private Map<String, String> attrs = new LinkedHashMap<>();
    private List<PlanNode> children = new ArrayList<>();

    public PlanNode(String op) {
        this.op = op;
    }

    public PlanNode attr(String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            attrs.put(key, String.valueOf(value));
        }
        return this;
    }
}
