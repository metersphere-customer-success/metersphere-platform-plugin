package io.metersphere.plugin.tapd.constants;

public class TapdUrl {

	public static final String AUTH = "/quickstart/testauth";

	public static final String GET_PROJECT_INFO = "/workspaces/get_workspace_info?workspace_id={1}";

	public static final String GET_WORKFLOW_FIRST_STEP = "/workflows/first_step?system={1}&workspace_id={2}";

	public static final String GET_WORKFLOW_TRANSITIONS = "/workflows/all_transitions?system={1}&workspace_id={2}";

	public static final String GET_WORKFLOW_STATUS_MAP = "/workflows/status_map?system={1}&workspace_id={2}";

	public static final String GET_PROJECT_USERS = "/workspaces/users?workspace_id={1}";

	public static final String GET_PROJECT_STORY = "/stories?workspace_id={1}&page={2}&limit={3}";

}
