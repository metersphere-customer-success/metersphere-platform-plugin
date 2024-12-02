package io.metersphere.platform.domain.response.rest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @program: metersphere-platform-plugin
 * @ClassName: ZentaoRestDemandResponse
 * @description:
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoRestDemandResponse extends ZentaoRestBaseResponse {

    private List<Story> stories;

    @Data
    public static class Story {
        private String id;
        private String title;
        private String plan;
        private List<Story> children;
    }
}
