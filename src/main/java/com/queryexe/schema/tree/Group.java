package com.queryexe.schema.tree;

import java.util.List;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class Group {
    private String title;
    private List<String> items;
}
