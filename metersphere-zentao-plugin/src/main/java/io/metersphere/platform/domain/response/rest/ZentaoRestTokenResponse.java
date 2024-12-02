package io.metersphere.platform.domain.response.rest;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @program: metersphere-platform-plugin
 * @ClassName: ZentaoRestTokenResponse
 * @description:
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoRestTokenResponse {

    private String token;
}
