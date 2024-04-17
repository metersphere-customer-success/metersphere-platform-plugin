package io.metersphere.plugin.jira.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class JiraCreateMetaField {

	@Setter
	@Getter
	public static class Field {
		private boolean required;
		private JiraCreateMetaField.Schema schema;
		private String name;
		private String key;
		private String autoCompleteUrl;
		private boolean hasDefaultValue;
		private Object defaultValue;
		private List<JiraCreateMetaField.AllowedValues> allowedValues;
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
		private List<JiraCreateMetaField.AllowedValues> children;
	}
}
