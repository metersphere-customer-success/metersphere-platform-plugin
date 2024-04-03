package io.metersphere.plugin.jira.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JiraCreateMetaFieldsResponse {

	private int startAt;
	private int maxResults;
	private int total;
	private List<Field> fields;
	/**
	 * 兼容一些非SASS环境, 及版本较低的环境
	 */
	private List<Field> values;

	@Setter
	@Getter
	public static class Field {
		private boolean required;
		private JiraCreateMetaFieldsResponse.Schema schema;
		private String name;
		private String key;
		private String autoCompleteUrl;
		private boolean hasDefaultValue;
		private Object defaultValue;
		private List<JiraCreateMetaFieldsResponse.AllowedValues> allowedValues;
		private String fieldId;
	}

	@Setter
	@Getter
	public static class Schema {
		private String type;
		private String items;
		private String custom;
		private int customId;
		private String system;
	}

	@Setter
	@Getter
	public static class AllowedValues {
		private String self;
		private String id;
		private String description;
		private String name;
		private String value;
		private boolean subtask;
		private int avatarId;
		private int hierarchyLevel;
		private List<JiraCreateMetaFieldsResponse.AllowedValues> children;
	}
}
