package io.metersphere.plugin.tapd.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class TapdIntegrationConfig {

	private String account;
	private String password;
}
