package io.metersphere.plugin.jira.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Setter
@Getter
public class JiraCreateMetaFields {

	private List<Projects> projects;

	@Setter
	@Getter
	public static class Projects {
		private List<Issuetypes> issuetypes;
	}

	@Setter
	@Getter
	public static class Issuetypes {
		private Map<String, JiraCreateMetaField.Field> fields;
	}
}
