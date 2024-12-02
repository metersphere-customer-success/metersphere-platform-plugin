package io.metersphere.platform.domain.response.json;

import io.metersphere.platform.domain.ZentaoResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @program: metersphere-platform-plugin
 * @ClassName: ZentaoBugResponse
 * @description:
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoBugResponse extends ZentaoResponse {
    @Data
    public static class Bug {
        private String id;
        private String title;
        private String steps;
        private String status;
        private String openedBy;
        private String openedDate;
        private String deleted;
        private String lastEditedDate;
        private String assignedTo;
    }
}
