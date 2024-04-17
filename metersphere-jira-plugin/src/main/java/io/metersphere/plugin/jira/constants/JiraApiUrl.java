package io.metersphere.plugin.jira.constants;

public class JiraApiUrl {

	/**
	 * @DEPRECATED 废弃接口保留, 兼容一些旧的版本
	 * 参考: <a href="https://developer.atlassian.com/cloud/jira/platform/rest/v2/api-group-issues/#api-rest-api-2-issue-createmeta-get">...</a>
	 */
	public static final String CREATE_META = "/issue/createmeta?projectKeys={1}&issuetypeIds={2}&expand=projects.issuetypes.fields";

	/**
	 * Get create field metadata for a project and issue type id
	 */
	public static final String CREATE_META_FOR_TYPE = "/issue/createmeta/{1}/issuetypes/{2}";

	/**
	 * Get project issue type
	 */
	public static final String GET_ISSUE_TYPE = "/issuetype/project?projectId={0}";
}
