package io.metersphere.plugin.tapd.domain.response;

import lombok.Data;

@Data
public class TapdBaseResponse<T> {

	private Integer status;

	private T data;

	private String info;
}
