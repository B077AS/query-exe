package com.queryexe.model.data;

import lombok.Data;

import java.util.List;

@Data
public class QueryData {

	private String query;
	private List<Object> parameters;

	public QueryData(String query, List<Object> parameters) {
		this.query = query;
		this.parameters = parameters;
	}

	@Override
	public String toString() {
		String formattedQuery = query;
		System.out.println(formattedQuery);
        for (Object param : parameters) {
            // Handle null parameters
            String paramString = param == null ? "NULL" : param.toString();
            // If it's not NULL, wrap it in quotes
            /*String replacement = param == null ? "NULL" : "'" + paramString + "'";
            formattedQuery = formattedQuery.replaceFirst("\\?", replacement);*/
            String replacement = param == null ? "NULL" : "'" + paramString.replace("$", "\\$") + "'";
            formattedQuery = formattedQuery.replaceFirst("\\?", replacement);

        }
        return formattedQuery;
	}
}
