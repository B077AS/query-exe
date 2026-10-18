package com.queryexe.model.data;

import lombok.Data;
import java.sql.DatabaseMetaData;
import java.util.List;

/**
 * A foreign key. {@code localColumn}/{@code referenceColumn} are the first column pair, which is all the
 * table editor handles; keys over several columns additionally carry every pair in {@code localColumns} /
 * {@code referenceColumns} (same order), and the referenced table's schema in {@code referenceSchema}.
 */
@Data
public class ForeignKeyData {

	private String constraintName;
	private String localColumn;
	private String referenceTable;
	private String referenceColumn;
	private String onDelete;
	private String onUpdate;

	/** Schema of the referenced table when it differs from (or isn't assumed to be) the table's own; may be null. */
	private String referenceSchema;
	/** All local columns in key order; null for single-column keys built without it. */
	private List<String> localColumns;
	/** All referenced columns in key order; null for single-column keys built without it. */
	private List<String> referenceColumns;

	public ForeignKeyData(String constraintName, String localColumn, String referenceTable,String referenceColumn, String onDelete, String onUpdate) {
		this.constraintName = constraintName;
		this.localColumn = localColumn;
		this.referenceTable = referenceTable;
		this.referenceColumn = referenceColumn;
		this.onDelete = onDelete;
		this.onUpdate = onUpdate;
	}

	public ForeignKeyData(String constraintName, String localColumn, String referenceTable, String referenceColumn, int deleteRule, int updateRule) {
		this.constraintName = constraintName;
		this.localColumn = localColumn;
		this.referenceTable = referenceTable;
		this.referenceColumn = referenceColumn;
		this.onDelete = convertRule(deleteRule);
		this.onUpdate = convertRule(updateRule);
	}

	/** Every local column of the key, in order (just {@code localColumn} for single-column keys). */
	public List<String> allLocalColumns() {
		return localColumns != null && !localColumns.isEmpty() ? localColumns : List.of(localColumn);
	}

	/** Every referenced column of the key, in order (just {@code referenceColumn} for single-column keys). */
	public List<String> allReferenceColumns() {
		return referenceColumns != null && !referenceColumns.isEmpty() ? referenceColumns : List.of(referenceColumn);
	}

	private String convertRule(int rule) {
		return switch (rule) {
		case DatabaseMetaData.importedKeyCascade -> "CASCADE";
		case DatabaseMetaData.importedKeySetNull -> "SET NULL";
		case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
		case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
		default -> "NO ACTION";
		};
	}
}
