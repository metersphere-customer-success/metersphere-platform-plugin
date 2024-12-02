package io.metersphere.platform.domain.response.rest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @program: metersphere-platform-plugin
 * @ClassName: ZentaoRestBuildResponse
 * @description:
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoRestBuildResponse extends ZentaoRestBaseResponse{
    private List<Build> builds;

    @Data
    public static class Build {
        private String id;
        private String name;
    }
}
