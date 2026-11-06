package io.github.b077as.queryexe.schema.tree;

import java.util.List;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class Tree {
    private String connectionId;
    private String name;
    private String type;
    private List<Container> containers;
    /** The container the connection is currently using, when known. */
    private String current;
}
