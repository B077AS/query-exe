package com.queryexe.model.data;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/** An index of a table: its name, its columns in key order and whether it enforces uniqueness. */
@Value
@Builder
public class IndexData {
	String name;
	boolean unique;
	/** True when the index backs the primary key (or, where the engine says so, was made for a constraint). */
	boolean primary;
	/** Index method where the engine reports one (BTREE, HASH, ...); may be null. */
	String type;
	List<String> columns;
}
