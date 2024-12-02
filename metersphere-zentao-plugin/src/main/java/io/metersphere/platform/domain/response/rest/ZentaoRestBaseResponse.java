package io.metersphere.platform.domain.response.rest;

import lombok.Data;

/**
 * @program: metersphere-platform-plugin
 * @ClassName: ZentaoRestBaseResponse
 * @description:
 */
@Data
public class ZentaoRestBaseResponse {
    private int page;

    private int total;

    private int limit;
}
