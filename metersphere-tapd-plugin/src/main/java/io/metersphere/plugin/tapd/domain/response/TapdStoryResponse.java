package io.metersphere.plugin.tapd.domain.response;

import lombok.Data;

import java.util.List;

@Data
public class TapdStoryResponse {

	private String id;
	private String name;
	private String children_id;
	private String parent_id;
	private List<TapdStoryResponse> children;
}
