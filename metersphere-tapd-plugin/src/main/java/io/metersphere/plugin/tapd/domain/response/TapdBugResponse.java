package io.metersphere.plugin.tapd.domain.response;

import lombok.Data;

@Data
public class TapdBugResponse {

	private String id;
	private String title;
	private String description;
	private String status;
	private String current_owner;
	private String workspace_id;
}
