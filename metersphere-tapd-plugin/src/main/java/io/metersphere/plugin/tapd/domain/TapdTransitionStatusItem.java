package io.metersphere.plugin.tapd.domain;

import lombok.Data;

@Data
public class TapdTransitionStatusItem {

	private String Name;
	private String StepPrevious;
	private String StepNext;
}
