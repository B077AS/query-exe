package com.queryexe.model.data;

import lombok.Data;
import java.sql.DatabaseMetaData;

@Data
public class ForeignKeyData {

	private String constraintName;
	private String localColumn;
	private String referenceTable;
	private String referenceColumn;
	private String onDelete;
	private String onUpdate;

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