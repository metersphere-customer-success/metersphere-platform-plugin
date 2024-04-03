package io.metersphere.plugin.jira.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JiraSprint {
	private Integer id;
	private String name;
	private String boardName;
}
