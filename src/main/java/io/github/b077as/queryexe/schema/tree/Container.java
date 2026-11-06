package io.github.b077as.queryexe.schema.tree;

import java.util.List;

/** A database (MySQL, MariaDB, SQL Server, H2, SQLite) or a schema (PostgreSQL): what the engine calls "databaseName" in its methods. */
@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class Container {
    private String name;
    /** "database" or "schema". */
    private String kind;
    /** The owning database when a PostgreSQL connection shows several databases, else null. */
    private String parent;
    private List<Table> tables;
    private List<Group> groups;
}
