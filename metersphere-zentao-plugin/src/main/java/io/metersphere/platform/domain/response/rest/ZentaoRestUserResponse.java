package io.metersphere.platform.domain.response.rest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @program: metersphere-platform-plugin
 * @ClassName: ZentaoRestUserResponse
 * @description:
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoRestUserResponse extends ZentaoRestBaseResponse {

    private List<User> users;

    @Data
    public static class User {
        private String id;
        private String account;
        private String realname;
    }
}
