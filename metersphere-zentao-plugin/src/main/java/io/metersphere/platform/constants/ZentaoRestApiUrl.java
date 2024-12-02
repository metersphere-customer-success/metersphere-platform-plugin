package io.metersphere.platform.constants;

/**
 * @program: metersphere-platform-plugin
 * @ClassName: ZentaoRestApiUrl
 * @description:
 */
public class ZentaoRestApiUrl {
    public static final String GET_TOKEN = "/tokens";
    public static final String GET_PRODUCT_OR_PROJECT = "/{1}";
    public static final String ADD_BUG = "/products/{1}/bugs";
    public static final String RESOLVE_BUG = "/bugs/{1}/resolve";
    public static final String CLOSE_BUG = "/bugs/{1}/close";
    public static final String ACTIVE_BUG = "/bugs/{1}/active";
    public static final String GET_OR_UPDATE_OR_DELETE_BUG = "/bugs/{1}";
    public static final String GET_USERS = "/users?page={1}&limit={2}";
    public static final String LIST_DEMAND = "/{1}/stories?page={2}&limit={3}";
    public static final String LIST_PLAN = "/products/{1}/plans?page={2}&limit={3}";
    public static final String GET_BUILDS = "/projects/{1}/builds";
    public static final String GET_EXECUTIONS = "/projects/{1}/executions";
    public static final String GET_PROJECT_STORIES = "/projects/{1}/stories";

}
