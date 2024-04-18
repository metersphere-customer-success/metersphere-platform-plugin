package io.metersphere.plugin.jira.impl;


import io.metersphere.plugin.jira.client.JiraDefaultClient;
import io.metersphere.plugin.jira.constants.*;
import io.metersphere.plugin.jira.domain.*;
import io.metersphere.plugin.jira.enums.JiraMetadataFieldType;
import io.metersphere.plugin.jira.enums.JiraOptionKey;
import io.metersphere.plugin.platform.dto.PlatformAttachment;
import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.dto.SyncBugResult;
import io.metersphere.plugin.platform.dto.request.*;
import io.metersphere.plugin.platform.dto.response.PlatformBugDTO;
import io.metersphere.plugin.platform.dto.response.PlatformBugUpdateDTO;
import io.metersphere.plugin.platform.dto.response.PlatformCustomFieldItemDTO;
import io.metersphere.plugin.platform.dto.response.PlatformDemandDTO;
import io.metersphere.plugin.platform.enums.PlatformCustomFieldType;
import io.metersphere.plugin.platform.enums.SyncAttachmentType;
import io.metersphere.plugin.platform.spi.AbstractPlatform;
import io.metersphere.plugin.platform.utils.PluginPager;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author song-cc-rock
 */
@Extension
public class JiraPlatform extends AbstractPlatform {

	protected JiraDefaultClient jiraClient;

	protected JiraProjectConfig projectConfig;

	protected SimpleDateFormat sdfWithZone = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	protected static final String MS_RICH_TEXT_PREVIEW_SRC_PREFIX = "/bug/attachment/preview/md";

	public JiraPlatform(PlatformRequest request) {
		super(request);
		JiraIntegrationConfig integrationConfig = getIntegrationConfig(request.getIntegrationConfig(), JiraIntegrationConfig.class);
		jiraClient = new JiraDefaultClient(integrationConfig);
	}

	/**
	 * 校验集成配置
	 */
	@Override
	public void validateIntegrationConfig() {
		jiraClient.auth();
	}

	/**
	 * 校验用户配置
	 *
	 * @param userConfig
	 */
	@Override
	public void validateUserConfig(String userConfig) {
		JiraUserPlatformInfo platformConfig = PluginUtils.parseObject(userConfig, JiraUserPlatformInfo.class);
		if (StringUtils.isBlank(platformConfig.getJiraAccount()) && StringUtils.isBlank(platformConfig.getJiraPassword())
				&& StringUtils.isBlank(platformConfig.getToken())) {
			throw new MSPluginException("JIRA认证失败: 账号或密码(Token)为空");
		}
		setUserConfig(userConfig, true);
		jiraClient.auth();
	}

	/**
	 * 校验项目配置
	 *
	 * @param projectConfigStr 项目配置信息
	 */
	@Override
	public void validateProjectConfig(String projectConfigStr) {
		try {
			JiraProjectConfig projectConfig = getProjectConfig(projectConfigStr);
			if (StringUtils.isBlank(projectConfig.getJiraKey())) {
				throw new MSPluginException("JIRA项目校验参数不能为空!");
			}
			JiraIssueProject project = jiraClient.getProject(projectConfig.getJiraKey());
			if (project == null || StringUtils.isBlank(project.getId())) {
				throw new MSPluginException("项目不存在");
			}
		} catch (Exception e) {
			throw new MSPluginException(e.getMessage());
		}
	}

	/**
	 * 设置用户平台配置
	 *
	 * @param userPlatformConfig 用户平台配置
	 */
	public void setUserConfig(String userPlatformConfig, Boolean isUserConfig) {
		JiraIntegrationConfig integrationConfig;
		if (isUserConfig) {
			// 如果是用户配置, 则从数据库中获取集成信息
			integrationConfig = getIntegrationConfig(JiraIntegrationConfig.class);
			JiraUserPlatformInfo userConfig = PluginUtils.parseObject(userPlatformConfig, JiraUserPlatformInfo.class);
			if (userConfig != null) {
				if (StringUtils.isNotBlank(userConfig.getJiraAccount())) {
					integrationConfig.setAccount(userConfig.getJiraAccount());
				}
				if (StringUtils.isNotBlank(userConfig.getJiraPassword())) {
					integrationConfig.setPassword(userConfig.getJiraPassword());
				}
				if (StringUtils.isNotBlank(userConfig.getToken())) {
					integrationConfig.setToken(userConfig.getToken());
				}
				if (StringUtils.isNotBlank(userConfig.getAuthType())) {
					integrationConfig.setAuthType(userConfig.getAuthType());
				}
			}
		} else {
			// 如果是集成配置, 则直接从参数中获取集成信息
			integrationConfig = getIntegrationConfig(userPlatformConfig, JiraIntegrationConfig.class);
		}
		validateAndSetConfig(integrationConfig);
	}

	/**
	 * 校验并设置集成配置
	 *
	 * @param config 集成配置
	 */
	private void validateAndSetConfig(JiraIntegrationConfig config) {
		jiraClient.initConfig(config);
	}

	/**
	 * 校验需求类型配置
	 */
	public void validateDemandType() {
		if (StringUtils.isBlank(projectConfig.getJiraDemandTypeId())) {
			throw new MSPluginException("请在项目中配置 Jira 需求类型!");
		}
	}

	/**
	 * 校验缺陷类型配置
	 */
	public void validateIssueType() {
		if (StringUtils.isBlank(projectConfig.getJiraBugTypeId())) {
			throw new MSPluginException("请在项目中配置 Jira 缺陷类型!");
		}
	}

	/**
	 * 校验项目Key配置
	 */
	public void validateProjectKey(String projectId) {
		if (StringUtils.isBlank(projectId)) {
			throw new MSPluginException("请在项目设置配置 Jira 项目ID");
		}
	}

	/**
	 * 是否支持第三方模板
	 *
	 * @return 支持第三方模板的平台才会在MS平台存在默认模板
	 */
	@Override
	public boolean isSupportDefaultTemplate() {
		// Jira 支持第三方默认模板
		return true;
	}

	/**
	 * 获取第三方平台缺陷的自定义字段
	 *
	 * @param projectConfigStr 项目配置信息
	 * @return 自定义字段集合
	 */
	@Override
	public List<PlatformCustomFieldItemDTO> getDefaultTemplateCustomField(String projectConfigStr) {
		projectConfig = getProjectConfig(projectConfigStr);
		Map<String, String> optionData = prepareOptionData();
		List<JiraCreateMetaField.Field> metaFields = jiraClient.getCreateMetadata(projectConfig.getJiraKey(),
				projectConfig.getJiraBugTypeId());

		List<PlatformCustomFieldItemDTO> fields = new ArrayList<>();
		Character filedKey = 'A';
		for (JiraCreateMetaField.Field item : metaFields) {
			JiraCreateMetaField.Schema schema = item.getSchema();
			PlatformCustomFieldItemDTO customField = new PlatformCustomFieldItemDTO();
			setCustomFieldBaseProperty(item, customField, filedKey);
			setCustomFieldTypeAndOption(schema, customField, item.getAllowedValues(), optionData);
			setCustomFieldDefaultValue(customField, item);
			fields.add(customField);
			filedKey = handleRelateField(customField, fields, filedKey, optionData);
		}

		// 类型为空的字段不展示
		fields = fields.stream().filter(i -> StringUtils.isNotBlank(i.getType())).collect(Collectors.toList());
		return sortCustomField(fields);
	}

	/**
	 * 获取表单项
	 *
	 * @param request 表单项请求参数
	 * @return 下拉项
	 */
	@Override
	public List<SelectOption> getFormOptions(GetOptionRequest request) {
		String method = request.getOptionMethod();
		try {
			// 这里反射调用 getIssueTypes 等方法，获取下拉框选项
			// noinspection unchecked
			return (List<SelectOption>) this.getClass().getMethod(method, request.getClass()).invoke(this, request);
		} catch (InvocationTargetException e) {
			PluginLogUtils.error(e.getTargetException());
			throw new MSPluginException(e.getTargetException());
		} catch (Exception e) {
			PluginLogUtils.error(e);
			throw new MSPluginException(e);
		}
	}

	/**
	 * 获取第三方平台状态列表
	 *
	 * @param projectConfig 项目配置信息
	 * @param issueKey      缺陷ID
	 * @return 状态列表
	 */
	@Override
	public List<SelectOption> getStatusTransitions(String projectConfig, String issueKey) {
		JiraProjectConfig config = getProjectConfig(projectConfig);
		List<SelectOption> statusOptions = new ArrayList<>();
		if (StringUtils.isBlank(issueKey)) {
			// 缺陷ID为空时, 获取所有状态列表
			List<JiraStatusResponse> statusResponseList = jiraClient.getStatus(config.getJiraKey());
			List<List<JiraStatusResponse.Statuses>> issueTypeStatus = statusResponseList.stream().filter(statusResponse -> StringUtils.equals(config.getJiraBugTypeId(), statusResponse.getId()))
					.map(JiraStatusResponse::getStatuses).toList();
			if (!CollectionUtils.isEmpty(issueTypeStatus)) {
				issueTypeStatus.forEach(item -> item.forEach(statuses -> {
					SelectOption option = new SelectOption();
					option.setText(statuses.getName());
					option.setValue(statuses.getId());
					statusOptions.add(option);
				}));
			}
		} else {
			// 缺陷ID不为空时, 获取状态流
			List<JiraTransitionsResponse.Transitions> transitions = jiraClient.getTransitions(issueKey);
			if (!CollectionUtils.isEmpty(transitions)) {
				transitions.forEach(item -> {
					SelectOption option = new SelectOption();
					option.setText(item.getTo().getName());
					option.setValue(item.getId());
					statusOptions.add(option);
				});
			}
		}
		return statusOptions;
	}

	/**
	 * 分页获取Jira需求
	 *
	 * @param request 需求请求参数
	 * @return 插件分页返回
	 */
	@Override
	public PluginPager<PlatformDemandDTO> pageDemand(DemandPageRequest request) {
		// validate demand config
		projectConfig = getProjectConfig(request.getProjectConfig());
		validateDemandType();
		List<Map<String, Object>> issues = queryAllDemand(request);
		if (CollectionUtils.isEmpty(issues)) {
			return new PluginPager<>(new PlatformDemandDTO(), 0, request.getPageSize(), request.getStartPage());
		}
		PlatformDemandDTO response = buildPlatformDemand(issues, null, request.getQuery(), request.getExcludeIds());
		int total = response.getList().size();
		if (request.isSelectAll()) {
			// no pager
			return new PluginPager<>(response, total, Integer.MAX_VALUE, request.getStartPage());
		} else {
			// set pager
			List<PlatformDemandDTO.Demand> pageDemands = response.getList().stream().skip((long) (request.getStartPage() - 1) * request.getPageSize()).limit(request.getPageSize()).collect(Collectors.toList());
			response.setList(pageDemands);
			return new PluginPager<>(response, total, request.getPageSize(), request.getStartPage());
		}
	}

	/**
	 * 获取需求集合(根据ID集合)
	 *
	 * @param request
	 * @return
	 */
	@Override
	public PlatformDemandDTO getDemands(DemandRelateQueryRequest request) {
		// validate demand config
		projectConfig = getProjectConfig(request.getProjectConfig());
		validateDemandType();
		// query demand list by ids
		Map<String, Object> bodyMap = jiraClient.pageDemand(projectConfig.getJiraKey(), projectConfig.getJiraDemandTypeId(), 1, Integer.MAX_VALUE, null);
		// handle empty data
		if (bodyMap == null) {
			return null;
		}
		List<Map<String, Object>> issues = (List<Map<String, Object>>) bodyMap.get("issues");
		if (CollectionUtils.isEmpty(issues)) {
			return null;
		}
		return buildPlatformDemand(issues, request.getRelateDemandIds(), null, null);
	}

	/**
	 * 新增缺陷
	 *
	 * @param request 缺陷请求参数
	 * @return 平台缺陷返回信息
	 */
	@Override
	public PlatformBugUpdateDTO addBug(PlatformBugUpdateRequest request) {
		// validate config
		validateConfig(request.getUserPlatformConfig(), request.getProjectConfig());

		// prepare and init jira param
		PlatformBugUpdateDTO platformBug = new PlatformBugUpdateDTO();
		// filter issue link field param
		List<PlatformCustomFieldItemDTO> issueLinkFields = filterIssueLinksField(request);
		// filter issue status transition field param
		PlatformCustomFieldItemDTO statusField = filterStatusTransition(request);
		// prepare issue rich text image name param
		List<String> remainImgNameFromRichText = new ArrayList<>();
		// filter issue custom field param
		Map<String, Object> paramMap = buildParamMap(request, projectConfig.getJiraBugTypeId(), projectConfig.getJiraKey(), platformBug, remainImgNameFromRichText);

		// add issue
		JiraAddIssueResponse result = jiraClient.addIssue(PluginUtils.toJSONString(paramMap), getFieldNameMap(request));
		// do transition
		Map<String, Object> transitionMap = new HashMap<>();
		if (statusField != null) {
			List<JiraTransitionsResponse.Transitions> transitions = jiraClient.getTransitions(result.getKey());
			if (!CollectionUtils.isEmpty(transitions)) {
				JiraTransitionsResponse.Transitions transition = transitions.stream().filter(item -> StringUtils.equals(item.getTo().getId(),
						statusField.getValue().toString())).findFirst().orElse(null);
				if (transition != null) {
					Map<String, String> status = new HashMap<>();
					status.put("id", transition.getId());
					transitionMap.put("transition", status);
					platformBug.setPlatformStatus(status.get("id"));
				}
			}
		}

		// Time-consuming, some operations are handle async;
		new Thread(() -> {
			// handle rich-text image (no-delete-image)
			request.getRichFileMap().values().forEach(file -> {
				// add image attachment
				jiraClient.uploadAttachment(result.getKey(), file);
			});

			// link issue
			if (!CollectionUtils.isEmpty(issueLinkFields)) {
				linkIssue(issueLinkFields, result.getKey(), jiraClient.getIssueLinkType());
			}
			// do transition
			if (statusField != null) {
				jiraClient.doTransitions(PluginUtils.toJSONString(transitionMap), result.getKey());
			}
		}).start();

		// return result
		platformBug.setPlatformBugKey(result.getKey());
		return platformBug;
	}

	/**
	 * 修改缺陷
	 *
	 * @param request 缺陷请求参数
	 * @return 平台缺陷更新返回信息
	 */
	@Override
	public PlatformBugUpdateDTO updateBug(PlatformBugUpdateRequest request) {
		// validate config
		validateConfig(request.getUserPlatformConfig(), request.getProjectConfig());

		// prepare and init jira param
		PlatformBugUpdateDTO platformBug = new PlatformBugUpdateDTO();
		// filter issue link field param
		List<PlatformCustomFieldItemDTO> issueLinkFields = filterIssueLinksField(request);
		// filter issue status transition field param
		PlatformCustomFieldItemDTO statusField = filterStatusTransition(request);
		// prepare issue rich text image name param
		List<String> remainImgNameFromRichText = new ArrayList<>();
		// filter issue custom field param
		Map<String, Object> param = buildParamMap(request, projectConfig.getJiraBugTypeId(), projectConfig.getJiraKey(), platformBug, remainImgNameFromRichText);

		// update issue
		jiraClient.updateIssue(request.getPlatformBugId(), PluginUtils.toJSONString(param), getFieldNameMap(request));
		// do transition
		if (statusField != null && statusField.getValue() != null) {
			platformBug.setPlatformStatus(statusField.getValue().toString());
		}

		// Time-consuming, some operations are handle async;
		new Thread(() -> {
			// handle rich-text image
			JiraIssue jiraIssue = jiraClient.getIssues(request.getPlatformBugId());
			Map<String, Object> fields = jiraIssue.getFields();
			List<Map<String, Object>> attachments = (List<Map<String, Object>>) fields.get(JiraMetadataField.ATTACHMENT_NAME);
			if (!CollectionUtils.isEmpty(attachments)) {
				// delete image attachment
				for (Map<String, Object> attachment : attachments) {
					String filename = attachment.get("filename").toString();
					String mimeType = attachment.get("mimeType").toString();
					if (StringUtils.startsWithIgnoreCase(mimeType, "image") && StringUtils.startsWithIgnoreCase(filename, "image")
							&& !remainImgNameFromRichText.contains(filename)) {
						// 只处理富文本生成的图片附件, 如果更新后不存在则移除, 其余类型文件暂不处理
						String fileId = attachment.get("id").toString();
						jiraClient.deleteAttachment(fileId);
					}
				}
			}

			if (!CollectionUtils.isEmpty(request.getRichFileMap())) {
				// add image attachment
				request.getRichFileMap().values().forEach(file -> {
					jiraClient.uploadAttachment(request.getPlatformBugId(), file);
				});
			}

			// link or unlink issue
			if (!CollectionUtils.isEmpty(issueLinkFields)) {
				// When edit, clean up all the last issue links and re-associate a group of issue links
				unLinkIssue(request.getPlatformBugId());
				linkIssue(issueLinkFields, request.getPlatformBugId(), jiraClient.getIssueLinkType());
			}

			// do transition
			if (statusField != null && statusField.getValue() != null) {
				Map<String, Object> transitionMap = new HashMap<>();
				Map<String, String> status = new HashMap<>();
				status.put("id", statusField.getValue().toString());
				transitionMap.put("transition", status);
				jiraClient.doTransitions(PluginUtils.toJSONString(transitionMap), request.getPlatformBugId());
			}
		}).start();

		// return result
		platformBug.setPlatformBugKey(request.getPlatformBugId());
		return platformBug;
	}

	/**
	 * 删除缺陷
	 *
	 * @param platformBugId 缺陷ID
	 */
	@Override
	public void deleteBug(String platformBugId) {
		jiraClient.deleteIssue(platformBugId);
	}

	/**
	 * 是否支持附件
	 *
	 * @return {true: 支持}
	 */
	@Override
	public boolean isSupportAttachment() {
		// Jira Rest-Api Support Attachment
		return true;
	}

	/**
	 * 同步附件至平台
	 *
	 * @param request 同步附件请求参数
	 */
	@Override
	public void syncAttachmentToPlatform(SyncAttachmentToPlatformRequest request) {
		String syncType = request.getSyncType();
		File file = request.getFile();
		if (StringUtils.equals(SyncAttachmentType.UPLOAD.syncOperateType(), syncType)) {
			// 上传附件
			jiraClient.uploadAttachment(request.getPlatformKey(), file);
		} else if (StringUtils.equals(SyncAttachmentType.DELETE.syncOperateType(), syncType)) {
			// 删除附件
			JiraIssue jiraIssue = jiraClient.getIssues(request.getPlatformKey());
			Map<String, Object> fields = jiraIssue.getFields();
			List<Map<String, Object>> attachments = (List<Map<String, Object>>) fields.get(JiraMetadataField.ATTACHMENT_NAME);
			if (!CollectionUtils.isEmpty(attachments)) {
				for (Map<String, Object> attachment : attachments) {
					String filename = attachment.get("filename").toString();
					if (StringUtils.equals(filename, file.getName())) {
						String fileId = attachment.get("id").toString();
						jiraClient.deleteAttachment(fileId);
					}
				}
			}
		}
	}

	/**
	 * 同步缺陷(存量)
	 *
	 * @param request 同步缺陷请求参数
	 * @return 同步缺陷结果
	 */
	@Override
	public SyncBugResult syncBugs(SyncBugRequest request) {
		SyncBugResult syncResult = new SyncBugResult();
		projectConfig = getProjectConfig(request.getProjectConfig());
		// 获取默认的模板字段
		List<PlatformCustomFieldItemDTO> defaultTemplateCustomField = getDefaultTemplateCustomField(request.getProjectConfig());
		List<PlatformBugDTO> syncBugs = request.getBugs();
		List<JiraTransitionsResponse.Transitions> transitions = new ArrayList<>();
		for (PlatformBugDTO syncBug : syncBugs) {
			try {
				JiraIssue jiraIssue = jiraClient.getIssues(syncBug.getPlatformBugId());
				Map<String, String> jiraIssueAttachmentMap = new HashMap<>();
				List attachments = (List) jiraIssue.getFields().get(JiraMetadataField.ATTACHMENT_NAME);
				if (!CollectionUtils.isEmpty(attachments)) {
					for (Object o : attachments) {
						Map attachment = (Map) o;
						jiraIssueAttachmentMap.put(attachment.get("filename").toString(), attachment.get("content").toString());
					}
				}

				if (jiraIssue != null && CollectionUtils.isEmpty(transitions)) {
					transitions = jiraClient.getTransitions(syncBug.getPlatformBugId());
				}
				syncJiraFieldToMsBug(syncBug, jiraIssue, defaultTemplateCustomField, jiraIssueAttachmentMap);
				// parse transition status
				syncBug.setStatus(parseTransitionStatus(transitions, syncBug.getStatus()));
				parseAttachmentToMsBug(syncResult, syncBug, jiraIssueAttachmentMap);
				// 同步的缺陷待更新
				syncResult.getUpdateBug().add(syncBug);
			} catch (HttpClientErrorException e) {
				if (HttpStatus.NOT_FOUND.isSameCodeAs(e.getStatusCode())) {
					// 缺陷未找到, 同步删除
					syncResult.getDeleteBugIds().add(syncBug.getId());
				}
			} catch (Exception e) {
				PluginLogUtils.error(e);
			}
		}
		return syncResult;
	}

	/**
	 * 同步缺陷(全量)
	 *
	 * @param request 同步全量缺陷请求参数
	 */
	@Override
	public void syncAllBugs(SyncAllBugRequest request) {
		// validate config
		projectConfig = getProjectConfig(request.getProjectConfig());
		validateProjectKey(projectConfig.getJiraKey());
		validateIssueType();

		// prepare page param
		int startAt = 0, maxResults = 100, currentSize = 0;
		// default template field
		List<PlatformCustomFieldItemDTO> defaultTemplateCustomField = getDefaultTemplateCustomField(request.getProjectConfig());
		do {
			// prepare post process func param
			List<PlatformBugDTO> needSyncBugs = new ArrayList<>();
			SyncBugResult syncBugResult = new SyncBugResult();

			// query jira bug by page
			JiraIssueListResponse result = jiraClient.getProjectIssues(startAt, maxResults, projectConfig.getJiraKey(), projectConfig.getJiraBugTypeId(), request);
			List<JiraIssue> jiraIssues = result.getIssues();
			if (CollectionUtils.isEmpty(jiraIssues)) {
				break;
			}

			currentSize = jiraIssues.size();
			if (!CollectionUtils.isEmpty(jiraIssues)) {
				// jira attachment field
				Map<String, Object> attachmentFieldMap = new HashMap<>();
				if (!jiraIssues.get(0).getFields().containsKey(JiraMetadataField.ATTACHMENT_NAME)) {
					// if jira not support attachment field, query attachment by issue key
					try {
						JiraIssueListResponse response = jiraClient.getProjectIssuesAttachment(startAt, maxResults, projectConfig.getJiraKey(),
								projectConfig.getJiraBugTypeId(), request);
						List<JiraIssue> jiraIssuesWithAttachmentField = response.getIssues();
						attachmentFieldMap = jiraIssuesWithAttachmentField.stream().collect(Collectors.toMap(JiraIssue::getKey, i -> i.getFields().get(JiraMetadataField.ATTACHMENT_NAME)));
					} catch (Exception e) {
						PluginLogUtils.error(e);
					}
				}
				// jira status transition
				List<JiraTransitionsResponse.Transitions> transitions = jiraClient.getTransitions(jiraIssues.get(0).getKey());

				for (JiraIssue jiraIssue : jiraIssues) {
					// prepare attachment param
					Map<String, String> jiraIssueAttachmentMap = new HashMap<>();
					List attachments = (List) jiraIssue.getFields().get(JiraMetadataField.ATTACHMENT_NAME);
					if (!CollectionUtils.isEmpty(attachments)) {
						for (Object o : attachments) {
							Map attachment = (Map) o;
							jiraIssueAttachmentMap.put(attachment.get("filename").toString(), attachment.get("content").toString());
						}
					}
					// transfer jira bug field to ms
					PlatformBugDTO msBug = new PlatformBugDTO();
					msBug.setId(UUID.randomUUID().toString());
					msBug.setPlatformDefaultTemplate(true);
					msBug.setPlatformBugId(jiraIssue.getKey());
					syncJiraFieldToMsBug(msBug, jiraIssue, defaultTemplateCustomField, jiraIssueAttachmentMap);
					// parse transition status
					msBug.setStatus(parseTransitionStatus(transitions, msBug.getStatus()));
					needSyncBugs.add(msBug);
					// handle attachment
					if (attachmentFieldMap.containsKey(jiraIssue.getKey())) {
						// set jira attachment field when jira issue not contain attachment field
						jiraIssue.getFields().put(JiraMetadataField.ATTACHMENT_NAME, attachmentFieldMap.get(jiraIssue.getKey()));
					}
					parseAttachmentToMsBug(syncBugResult, msBug, jiraIssueAttachmentMap);
				}
			}

			// set post process func param
			// common sync post param {syncBugs: all need sync bugs, attachmentMap: all bug attachment}
			SyncPostParamRequest syncPostParamRequest = new SyncPostParamRequest();
			syncPostParamRequest.setNeedSyncBugs(needSyncBugs);
			syncPostParamRequest.setAttachmentMap(syncBugResult.getAttachmentMap());
			request.getSyncPostProcessFunc().accept(syncPostParamRequest);
			startAt += maxResults;
		} while (currentSize >= maxResults);
	}

	/**
	 * 获取附件内容
	 *
	 * @param fileKey            附件ID
	 * @param inputStreamHandler 处理方法
	 */
	@Override
	public void getAttachmentContent(String fileKey, Consumer<InputStream> inputStreamHandler) {
		jiraClient.getAttachmentContent(fileKey, inputStreamHandler);
	}

	/**
	 * 获取指派人选项值
	 *
	 * @param request 查询参数
	 * @return 选项集合
	 */
	public List<SelectOption> getAssignableOptions(GetOptionRequest request) {
		JiraProjectConfig projectConfig = getProjectConfig(request.getProjectConfig());
		return getAssignableOptions(projectConfig.getJiraKey(), request.getQuery());
	}

	/**
	 * 获取指派人选项值
	 *
	 * @param jiraKey 项目KEY
	 * @param query   查询参数
	 * @return 选项集合
	 */
	private List<SelectOption> getAssignableOptions(String jiraKey, String query) {
		List<JiraUser> userOptions = jiraClient.assignableUserSearch(jiraKey, query);
		return handleUserOptions(userOptions);
	}

	/**
	 * 获取用户选项值
	 *
	 * @param request 查询参数
	 * @return 选项集合
	 */
	public List<SelectOption> getUserSearchOptions(GetOptionRequest request) {
		return getUserSearchOptions(request.getQuery());
	}

	/**
	 * 获取用户选项值
	 *
	 * @param query 查询参数
	 * @return 选项集合
	 */
	private List<SelectOption> getUserSearchOptions(String query) {
		List<JiraUser> reportOptions = jiraClient.allUserSearch(query);
		return handleUserOptions(reportOptions);
	}

	/**
	 * 获取链接事务选项值
	 *
	 * @param request 请求参数
	 * @return 选项集合
	 */
	public List<SelectOption> getIssueLinkOptions(GetOptionRequest request) {
		return getIssueLinkOptions("", request.getQuery());
	}

	/**
	 * 获取链接事务选项值
	 *
	 * @param currentIssueKey 当前缺陷ID
	 * @param query           查询参数
	 * @return 选项集合
	 */
	public List<SelectOption> getIssueLinkOptions(String currentIssueKey, String query) {
		return jiraClient.getIssueLinks(currentIssueKey, query)
				.stream()
				.map(item -> new SelectOption(item.getKey() + StringUtils.SPACE + item.getSummary(), item.getKey())).toList();
	}

	/**
	 * 获取Issue链接事务类型选项
	 *
	 * @return 选项集合
	 */
	public List<SelectOption> getIssueLinkTypeOptions() {
		List<SelectOption> selectOptions = new ArrayList<>();
		List<JiraIssueLinkTypeResponse.IssueLinkType> issueLinkTypes = jiraClient.getIssueLinkType();
		for (JiraIssueLinkTypeResponse.IssueLinkType issueLinkType : issueLinkTypes) {
			if (StringUtils.equals(issueLinkType.getInward(), issueLinkType.getOutward())) {
				selectOptions.add(new SelectOption(issueLinkType.getInward(), issueLinkType.getInward()));
			} else {
				selectOptions.add(new SelectOption(issueLinkType.getInward(), issueLinkType.getInward()));
				selectOptions.add(new SelectOption(issueLinkType.getOutward(), issueLinkType.getOutward()));
			}
		}
		return selectOptions;
	}

	/**
	 * 获取sprint选项值
	 *
	 * @param request 请求参数
	 * @return 选项集合
	 */
	public List<SelectOption> getSprintOptions(GetOptionRequest request) {
		return jiraClient.getSprint(request.getQuery())
				.stream()
				.map(sprint -> new SelectOption(StringUtils.join(sprint.getName(), " (", sprint.getBoardName(), ")"), sprint.getId().toString()))
				.collect(Collectors.toList());
	}

	/**
	 * 获取Jira缺陷类型
	 *
	 * @param request 插件下拉值请求参数
	 * @return 下拉选项
	 */
	public List<SelectOption> getBugType(PluginOptionsRequest request) {
		JiraProjectConfig projectConfig = getProjectConfig(request.getProjectConfig());
		return jiraClient.getIssueType(projectConfig.getJiraKey())
				.stream()
				.map(item -> new SelectOption(item.getName(), item.getId()))
				.collect(Collectors.toList());
	}

	private List<Map<String, Object>> queryAllDemand(DemandPageRequest request) {
		List<Map<String, Object>> allIssues = new ArrayList<>();
		// 由于Jira接口限制, 一次最多返回100条数据
		int maxResult = 100;
		// query demand list
		Map<String, Object> bodyMap = jiraClient.pageDemand(projectConfig.getJiraKey(), projectConfig.getJiraDemandTypeId(),
				0, maxResult, null);
		// handle empty data
		if (bodyMap == null) {
			return allIssues;
		}
		List<Map<String, Object>> issues = (List<Map<String, Object>>) bodyMap.get("issues");
		if (CollectionUtils.isEmpty(issues)) {
			return allIssues;
		}
		allIssues.addAll(issues);
		int total = (Integer) bodyMap.get("total");
		// query next page
		if (total > maxResult) {
			int totalPage = (int) Math.ceil((double) total / maxResult);
			for (int i = 2; i <= totalPage; i++) {
				Map<String, Object> nextBodyMap = jiraClient.pageDemand(projectConfig.getJiraKey(), projectConfig.getJiraDemandTypeId(),
						(i - 1) * maxResult, maxResult, null);
				if (nextBodyMap != null) {
					List<Map<String, Object>> nextIssues = (List<Map<String, Object>>) nextBodyMap.get("issues");
					if (!CollectionUtils.isEmpty(nextIssues)) {
						allIssues.addAll(nextIssues);
					}
				}
			}
		}
		return allIssues;
	}

	/**
	 * 构建平台缺陷需求
	 *
	 * @param issues    需求列表
	 * @param filterIds 过滤的需求ID
	 * @return
	 */
	private PlatformDemandDTO buildPlatformDemand(List<Map<String, Object>> issues, List<String> filterIds, String query, List<String> excludeIds) {
		// prepare custom headers
		List<PlatformCustomFieldItemDTO> customHeaders = new ArrayList<>();
		// prepare demand list
		List<PlatformDemandDTO.Demand> demands = new ArrayList<>();
		issues.forEach(issue -> {
			// Jira目前只满足第一层级需求, 父子层级看后续需求
			PlatformDemandDTO.Demand demand = new PlatformDemandDTO.Demand();
			demand.setDemandId(issue.get("key").toString());
			// noinspection unchecked
			Map<String, Object> fieldMap = (Map<String, Object>) issue.get("fields");
			demand.setDemandName(fieldMap.get("summary").toString());
			demand.setDemandUrl(jiraClient.getBaseDemandUrl() + "/jira/software/projects/" + projectConfig.getJiraKey() + "/issues/" + issue.get("key").toString());
			boolean isDemandShow = (StringUtils.isBlank(query) || StringUtils.containsIgnoreCase(demand.getDemandId(), query) || StringUtils.containsIgnoreCase(demand.getDemandName(), query)) &&
					(CollectionUtils.isEmpty(excludeIds) || !excludeIds.contains(demand.getDemandId()));
			if (isDemandShow) {
				demands.add(demand);
			}
		});
		// sort by demand id
		demands.sort(Comparator.comparing(PlatformDemandDTO.Demand::getDemandId));
		// filter demand list by ids
		List<PlatformDemandDTO.Demand> filterDemands = demands;
		if (!CollectionUtils.isEmpty(filterIds)) {
			filterDemands = filterDemands.stream().filter(item -> filterIds.contains(item.getDemandId())).collect(Collectors.toList());
		}
		// set demand response
		PlatformDemandDTO response = new PlatformDemandDTO();
		response.setList(filterDemands);
		response.setCustomHeaders(customHeaders);
		return response;
	}

	/**
	 * 获取项目配置
	 *
	 * @param configStr 项目配置JSON
	 * @return 项目配置对象
	 */
	private JiraProjectConfig getProjectConfig(String configStr) {
		if (StringUtils.isBlank(configStr)) {
			throw new MSPluginException("请在项目中添加项目配置！");
		}
		return PluginUtils.parseObject(configStr, JiraProjectConfig.class);
	}

	/**
	 * 设置基础自定义字段属性
	 *
	 * @param item        字段项
	 * @param customField 自定义字段
	 * @param name        名称
	 * @param filedKey    唯一KEY
	 */
	private void setCustomFieldBaseProperty(JiraCreateMetaField.Field item, PlatformCustomFieldItemDTO customField, Character filedKey) {
		customField.setId(item.getFieldId());
		customField.setName(item.getName());
		customField.setKey(String.valueOf(filedKey++));
		customField.setCustomData(item.getFieldId());
		if (StringUtils.equals(JiraMetadataField.SUMMARY_FIELD_NAME, item.getFieldId())) {
			customField.setRequired(true);
		} else {
			customField.setRequired(item.isRequired());
		}
		if (StringUtils.equalsAnyIgnoreCase(item.getFieldId(), JiraMetadataField.SUMMARY_FIELD_NAME, JiraMetadataField.DESCRIPTION_FIELD_NAME, JiraMetadataField.ENVIRONMENT_FIELD_NAME)) {
			customField.setSystemField(true);
		} else {
			customField.setSystemField(false);
		}
	}

	/**
	 * 设置自定义字段类型和选项
	 *
	 * @param schema        jira field schema
	 * @param customField   自定义字段
	 * @param allowedValues 允许的选项值
	 */
	private void setCustomFieldTypeAndOption(JiraCreateMetaField.Schema schema, PlatformCustomFieldItemDTO customField,
											 List<JiraCreateMetaField.AllowedValues> allowedValues, Map<String, String> optionData) {
		Set<String> specialCustomFieldType = new HashSet<>(JiraMetadataSpecialCustomField.getSpecialFields());
		String customType = schema.getCustom();
		if (StringUtils.isNotBlank(customType)) {
			// Jira自定义字段
			customField.setType(JiraMetadataFieldType.mappingJiraCustomType(customType));
			if (mappingSpecialField(specialCustomFieldType, customType)) {
				// 特殊自定义字段类型
				handleSpecialCustomFieldType(schema, customField, optionData);
			}
		} else {
			// Jira系统字段
			customField.setType(JiraMetadataFieldType.mappingJiraCustomType(customField.getId()));
			Set<String> specialSystemFieldType = new HashSet<>(JiraMetadataSpecialSystemField.getSpecialFields());
			if (specialSystemFieldType.contains(schema.getType()) || specialSystemFieldType.contains(schema.getSystem())) {
				// 特殊系统字段类型
				handleSpecialSystemFieldType(schema, customField, optionData);
			}
			if (StringUtils.isNotBlank(schema.getType()) && StringUtils.isBlank(customField.getType())) {
				customField.setType(JiraMetadataFieldType.mappingJiraCustomType(schema.getType()));
			}
		}
		// 如果是MULTIPLE_INPUT类型, 则设置placeholder
		if (StringUtils.equals(customField.getType(), PlatformCustomFieldType.MULTIPLE_INPUT.name())) {
			customField.setPlaceHolder(JiraMetadataFieldPlaceHolder.TAG_PLACEHOLDER);
		}
		// 如果是textfield类型且为预估时间, 则设置placeholder
		if (StringUtils.equals(customField.getName(), JiraMetadataField.INITIAL_ESTIMATE_FIELD_ZH)) {
			customField.setPlaceHolder(JiraMetadataFieldPlaceHolder.ESTIMATE_FIELD_PLACEHOLDER);
		}

		// allowedValues不为空时, 替换options
		if (!CollectionUtils.isEmpty(allowedValues)) {
			customField.setOptions(PluginUtils.toJSONString(handleAllowedValuesOptions(allowedValues)));
		}
	}

	/**
	 * 处理特殊自定义字段类型
	 *
	 * @param schema      jira field schema
	 * @param customField 自定义字段
	 */
	private void handleSpecialCustomFieldType(JiraCreateMetaField.Schema schema, PlatformCustomFieldItemDTO customField, Map<String, String> optionData) {
		String customType = schema.getCustom();
		String specialCustomFieldTypesOfSchemaType = "project";
		String arraySchemaType = "array";

		if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.MULTI_CHECK_BOX)) {
			// 多选复选框, 需设置默认值为空数组
			customField.setType(PlatformCustomFieldType.CHECKBOX.name());
			customField.setDefaultValue(PluginUtils.toJSONString(new ArrayList<>()));
		}

		if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.CUSTOM_FIELD_TYPES) && StringUtils.equals(schema.getType(), specialCustomFieldTypesOfSchemaType)) {
			// 插件字段类型为{自定义字段类型获取}
			customField.setType(PlatformCustomFieldType.SELECT.name());
		}

		if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.PEOPLE)) {
			// PEOPLE类型特殊字段
			if (StringUtils.isNotBlank(schema.getType()) && StringUtils.equals(schema.getType(), arraySchemaType)) {
				customField.setType(PlatformCustomFieldType.MULTIPLE_SELECT.name());
			} else {
				customField.setType(PlatformCustomFieldType.SELECT.name());
			}
			customField.setSupportSearch(true);
			customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_USER);
			customField.setOptions(optionData.get(JiraOptionKey.USER.name()));
		}

		if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.USER_PICKER)) {
			// 自定义字段类型为用户选择器
			customField.setType(PlatformCustomFieldType.SELECT.name());
			customField.setSupportSearch(true);
			customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_USER);
			customField.setOptions(optionData.get(JiraOptionKey.USER.name()));
		}

		if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.MULTI_USER_PICKER)) {
			// 自定义字段类型为多用户选择器
			customField.setType(PlatformCustomFieldType.MULTIPLE_SELECT.name());
			customField.setSupportSearch(true);
			customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_USER);
			customField.setOptions(optionData.get(JiraOptionKey.USER.name()));
		}

		if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.SPRINT_FIELD_NAME)) {
			// 自定义字段类型为sprint
			customField.setOptions(optionData.get(JiraOptionKey.SPRINT.name()));
			customField.setSupportSearch(true);
			customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_SPRINT);
			customField.setType(PlatformCustomFieldType.SELECT.name());
		}

		if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.EPIC_LINK)) {
			// 自定义字段类型为epic-link
			customField.setOptions(optionData.get(JiraOptionKey.EPIC.name()));
			customField.setType(PlatformCustomFieldType.SELECT.name());
		}
	}

	/**
	 * 处理特殊系统字段
	 *
	 * @param schema      jira field schema
	 * @param customField 自定义字段
	 */
	private void handleSpecialSystemFieldType(JiraCreateMetaField.Schema schema, PlatformCustomFieldItemDTO customField, Map<String, String> optionData) {
		if (StringUtils.equals(schema.getType(), JiraMetadataSpecialSystemField.TIME_TRACKING)) {
			// TIME_TRACKING (特殊系统字段)
			customField.setId(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID);
			customField.setCustomData(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID);
			customField.setName(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME);
			customField.setType(PlatformCustomFieldType.INPUT.name());
			customField.setPlaceHolder(JiraMetadataFieldPlaceHolder.ESTIMATE_FIELD_PLACEHOLDER);
		}

		if (StringUtils.equals(schema.getSystem(), JiraMetadataSpecialSystemField.ASSIGNEE)) {
			customField.setSupportSearch(true);
			customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_ASSIGNABLE);
			customField.setOptions(optionData.get(JiraOptionKey.ASSIGN.name()));
			customField.setType(PlatformCustomFieldType.SELECT.name());
		}

		if (StringUtils.equals(schema.getSystem(), JiraMetadataSpecialSystemField.REPORTER)) {
			customField.setSupportSearch(true);
			customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_USER);
			customField.setOptions(optionData.get(JiraOptionKey.USER.name()));
			customField.setType(PlatformCustomFieldType.SELECT.name());
		}

		if (StringUtils.equals(schema.getSystem(), JiraMetadataSpecialSystemField.ISSUE_LINKS)) {
			customField.setSupportSearch(true);
			customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_ISSUE_LINK);
			customField.setOptions(optionData.get(JiraOptionKey.ISSUE_LINK.name()));
			customField.setType(PlatformCustomFieldType.MULTIPLE_SELECT.name());
			customField.setPlaceHolder(JiraMetadataFieldPlaceHolder.ISSUE_LINK_TYPE_PLACEHOLDER);
		}
	}

	/**
	 * 设置自定义字段默认值
	 *
	 * @param customField 自定义字段
	 * @param item        Jira字段
	 */
	private void setCustomFieldDefaultValue(PlatformCustomFieldItemDTO customField, JiraCreateMetaField.Field item) {
		if (item.isHasDefaultValue()) {
			Object defaultValue = item.getDefaultValue();
			if (defaultValue != null) {
				String msDefaultValue;
				if (defaultValue instanceof Map) {
					// noinspection unchecked
					Object val = ((Map<String, Object>) defaultValue).get("id");
					if (val instanceof String) {
						msDefaultValue = (String) val;
					} else {
						msDefaultValue = PluginUtils.toJSONString(val);
					}
				} else if (defaultValue instanceof List) {
					List<Object> defaultList = new ArrayList<>();
					// noinspection unchecked
					((List<Object>) defaultValue).forEach(i -> {
						if (i instanceof Map) {
							// noinspection unchecked
							defaultList.add(((Map<String, Object>) i).get("id"));
						} else {
							defaultList.add(i);
						}
					});
					msDefaultValue = PluginUtils.toJSONString(defaultList);
				} else {
					if (customField.getType().equals(PlatformCustomFieldType.DATE.name())) {
						if (defaultValue instanceof String) {
							msDefaultValue = (String) defaultValue;
						} else {
							LocalDate defaultDate = Instant.ofEpochMilli((Long) defaultValue).atZone(ZoneId.systemDefault()).toLocalDate();
							msDefaultValue = defaultDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
						}
					} else if (customField.getType().equals(PlatformCustomFieldType.DATETIME.name())) {
						if (defaultValue instanceof String) {
							msDefaultValue = (String) defaultValue;
						} else {
							LocalDateTime defaultDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli((Long) defaultValue), ZoneId.systemDefault());
							msDefaultValue = defaultDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
						}
					} else {
						msDefaultValue = (String) defaultValue;
					}
				}
				customField.setDefaultValue(msDefaultValue);
			}
		}
	}

	/**
	 * 处理自定义关联字段
	 *
	 * @param item   当前字段
	 * @param fields 字段集合
	 * @param key    唯一KEY
	 * @return 生成后的KEY
	 */
	private Character handleRelateField(PlatformCustomFieldItemDTO item, List<PlatformCustomFieldItemDTO> fields, Character key, Map<String, String> optionData) {
		String id = item.getId();
		if (StringUtils.equals(id, JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID)) {
			// add original_estimate relate field
			PlatformCustomFieldItemDTO remainingEstimate = new PlatformCustomFieldItemDTO();
			BeanUtils.copyProperties(item, remainingEstimate);
			remainingEstimate.setId(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID);
			remainingEstimate.setCustomData(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID);
			remainingEstimate.setName(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_NAME);
			remainingEstimate.setKey(String.valueOf(key++));
			remainingEstimate.setPlaceHolder(JiraMetadataFieldPlaceHolder.ESTIMATE_FIELD_PLACEHOLDER);
			fields.add(remainingEstimate);
		}

		if (StringUtils.equals(id, JiraMetadataSpecialSystemField.ISSUE_LINKS)) {
			// add issue link relate field
			PlatformCustomFieldItemDTO issueLinkField = new PlatformCustomFieldItemDTO();
			issueLinkField.setId(JiraMetadataField.ISSUE_LINK_TYPE);
			issueLinkField.setName(JiraMetadataField.ISSUE_LINK_TYPE_COLUMN_ZH);
			issueLinkField.setCustomData(JiraMetadataField.ISSUE_LINK_TYPE);
			issueLinkField.setRequired(false);
			issueLinkField.setOptions(optionData.get(JiraOptionKey.ISSUE_LINK_TYPE.name()));
			issueLinkField.setType(PlatformCustomFieldType.SELECT.name());
			issueLinkField.setKey(String.valueOf(key++));
			fields.add(issueLinkField);
		}

		return key;
	}

	/**
	 * 排序自定义字段
	 *
	 * @param fields 字段集合
	 * @return 排序后的字段集合
	 */
	private List<PlatformCustomFieldItemDTO> sortCustomField(List<PlatformCustomFieldItemDTO> fields) {
		// 按类型排序 (富文本排最后, 缺陷链接事务其次, INPUT最前面, SUMMARY其次, 其余字段按默认排序)
		fields.sort((a, b) -> {
			if (a.getType().equals(PlatformCustomFieldType.RICH_TEXT.name())) {
				return 1;
			}
			if (b.getType().equals(PlatformCustomFieldType.RICH_TEXT.name())) {
				return -1;
			}
			if (a.getId().equals(JiraMetadataSpecialSystemField.ISSUE_LINKS)) {
				return 1;
			}
			if (b.getId().equals(JiraMetadataSpecialSystemField.ISSUE_LINKS)) {
				return -1;
			}
			if (a.getId().equals(JiraMetadataField.ISSUE_LINK_TYPE)) {
				return 1;
			}
			if (b.getId().equals(JiraMetadataField.ISSUE_LINK_TYPE)) {
				return -1;
			}
			if (a.getId().equals(JiraMetadataField.SUMMARY_FIELD_NAME)) {
				return -1;
			}
			if (b.getId().equals(JiraMetadataField.SUMMARY_FIELD_NAME)) {
				return 1;
			}
			if (a.getType().equals(PlatformCustomFieldType.INPUT.name())) {
				return -1;
			}
			if (b.getType().equals(PlatformCustomFieldType.INPUT.name())) {
				return 1;
			}
			return a.getType().compareTo(b.getType());
		});
		return fields;
	}

	/**
	 * 处理Jira用户选项
	 *
	 * @param userList 用户列表
	 * @return 用户选项集合
	 */
	private List<SelectOption> handleUserOptions(List<JiraUser> userList) {
		List<SelectOption> options = new ArrayList<>();
		userList.forEach(user -> {
			SelectOption selectOption = new SelectOption();
			// set option value
			if (StringUtils.isNotBlank(user.getAccountId())) {
				selectOption.setValue(user.getAccountId());
			} else {
				selectOption.setValue(user.getName());
			}
			// set option text
			if (StringUtils.isNotBlank(user.getEmailAddress())) {
				selectOption.setText(user.getDisplayName() + " (" + user.getEmailAddress() + ")");
			} else {
				selectOption.setText(user.getDisplayName());
			}
			options.add(selectOption);
		});
		return options;
	}

	/**
	 * 处理Jira字段允许的选项值
	 *
	 * @param allowedValues 允许的选项值
	 * @return 选项集合
	 */
	private List<JiraAllowedValueOption> handleAllowedValuesOptions(List<JiraCreateMetaField.AllowedValues> allowedValues) {
		if (CollectionUtils.isEmpty(allowedValues)) {
			return null;
		}
		List<JiraAllowedValueOption> allowedValueOptions = new ArrayList<>();
		allowedValues.forEach(val -> {
			JiraAllowedValueOption allowedValueOption = new JiraAllowedValueOption();
			allowedValueOption.setValue(val.getId());
			if (StringUtils.isNotBlank(val.getName())) {
				allowedValueOption.setText(val.getName());
			} else {
				allowedValueOption.setText(val.getValue());
			}
			allowedValueOption.setChildren(handleAllowedValuesOptions(val.getChildren()));
			allowedValueOptions.add(allowedValueOption);
		});
		return allowedValueOptions;
	}

	/**
	 * 校验配置
	 *
	 * @param userPlatformConfig 用户平台配置
	 * @param projectConfig      项目配置
	 */
	private void validateConfig(String userPlatformConfig, String projectConfig) {
		setUserConfig(userPlatformConfig, true);
		this.projectConfig = getProjectConfig(projectConfig);
		validateProjectKey(this.projectConfig.getJiraKey());
		validateIssueType();
	}

	/**
	 * 过滤issue-link字段
	 *
	 * @param request 请求参数
	 * @return issue-link自定义字段
	 */
	private List<PlatformCustomFieldItemDTO> filterIssueLinksField(PlatformBugUpdateRequest request) {
		if (!CollectionUtils.isEmpty(request.getCustomFieldList())) {
			// remove and return issue link field
			List<PlatformCustomFieldItemDTO> issueLinkFields = request.getCustomFieldList().stream().filter(item ->
					StringUtils.equalsAny(item.getCustomData(), JiraMetadataField.ISSUE_LINK_TYPE, JiraMetadataSpecialSystemField.ISSUE_LINKS)).toList();
			request.getCustomFieldList().removeAll(issueLinkFields);
			return issueLinkFields;
		} else {
			return new ArrayList<>();
		}
	}

	/**
	 * 过滤状态Transition字段
	 *
	 * @param request 请求参数
	 * @return 状态自定义字段
	 */
	private PlatformCustomFieldItemDTO filterStatusTransition(PlatformBugUpdateRequest request) {
		if (!CollectionUtils.isEmpty(request.getCustomFieldList())) {
			// remove and return issue status
			List<PlatformCustomFieldItemDTO> statusList = request.getCustomFieldList().stream().filter(item ->
					StringUtils.equals(item.getCustomData(), "status")).toList();
			request.getCustomFieldList().removeAll(statusList);
			return statusList.get(0);
		} else {
			return null;
		}
	}

	/**
	 * 构建参数Map{新增, 修改缺陷}
	 *
	 * @param request     请求参数
	 * @param issueTypeId 缺陷类型
	 * @param jiraKey     缺陷唯一KEY
	 * @param platformBug 平台缺陷
	 * @return 参数Map
	 */
	private Map<String, Object> buildParamMap(PlatformBugUpdateRequest request, String issueTypeId, String jiraKey,
											  PlatformBugUpdateDTO platformBug, List<String> remainImgNameFromRichText) {
		Map<String, Object> issuetype = new LinkedHashMap<>();
		issuetype.put("id", issueTypeId);
		Map<String, Object> project = new LinkedHashMap<>();
		project.put("key", jiraKey);

		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("project", project);
		fields.put("issuetype", issuetype);

		Map<String, Object> param = new LinkedHashMap<>();
		param.put("fields", fields);

		parseField(request, fields, platformBug, remainImgNameFromRichText);
		setSpecialParam(fields);
		return param;
	}

	/**
	 * 解析自定义字段
	 *
	 * @param request     请求参数
	 * @param fields      字段集合
	 * @param platformBug 平台缺陷
	 */
	private void parseField(PlatformBugUpdateRequest request, Map<String, Object> fields, PlatformBugUpdateDTO platformBug, List<String> remainImgNameFromRichText) {
		List<PlatformCustomFieldItemDTO> customFields = request.getCustomFieldList();
		Map<String, String> platformCustomFieldMap = new HashMap<>();
		if (CollectionUtils.isEmpty(customFields)) {
			PluginLogUtils.error("Jira缺陷字段为空, 请检查参数!");
			throw new MSPluginException("Jira缺陷字段为空, 请检查参数!");
		}

		Iterator<PlatformCustomFieldItemDTO> iterator = customFields.iterator();
		while (iterator.hasNext()) {
			PlatformCustomFieldItemDTO item = iterator.next();
			String fieldName = item.getCustomData();
			if (StringUtils.isEmpty(item.getCustomData()) || StringUtils.isEmpty(item.getType()) || ObjectUtils.isEmpty(item.getValue())) {
				continue;
			}
			// 把不同字段类型, 解析成Jira保存缺陷数据时的结构
			if (StringUtils.equalsAnyIgnoreCase(item.getType(), PlatformCustomFieldType.SELECT.name(),
					PlatformCustomFieldType.RADIO.name(), PlatformCustomFieldType.MEMBER.name())) {
				if (StringUtils.equals(fieldName, JiraMetadataSpecialSystemField.ASSIGNEE)) {
					platformBug.setPlatformHandleUser(item.getValue().toString());
					// 如果是插件内置的处理人(Jira指派人), 则移除自定义字段中的处理人
					if (!StringUtils.equals(item.getName().toString(), JiraMetadataSpecialSystemField.ASSIGNEE_ZH)) {
						iterator.remove();
					}
				}
				Map<String, Object> param = new LinkedHashMap<>();
				param.put("id", item.getValue());
				fields.put(fieldName, param);
			} else if (StringUtils.equalsAnyIgnoreCase(item.getType(), PlatformCustomFieldType.MULTIPLE_SELECT.name(),
					PlatformCustomFieldType.CHECKBOX.name(), PlatformCustomFieldType.MULTIPLE_MEMBER.name())) {
				List<Map<String, Object>> attrs = new ArrayList<>();
				List<Object> values = PluginUtils.parseArray((String) item.getValue(), Object.class);
				values.forEach(v -> {
					Map<String, Object> param = new LinkedHashMap<>();
					param.put("id", v);
					attrs.add(param);
				});
				fields.put(fieldName, attrs);
			} else if (StringUtils.equalsIgnoreCase(item.getType(), PlatformCustomFieldType.CASCADER.name())) {
				// 级联类型, 通常默认为["父级", "子级"]这样的结构处理
				Map<String, Object> attr = new LinkedHashMap<>();
				List<Object> values = PluginUtils.parseArray((String) item.getValue(), Object.class);
				if (CollectionUtils.isEmpty(values)) {
					attr.put("id", item.getValue());
				} else {
					// 父级
					attr.put("id", values.get(0));
					// 子级
					if (values.size() > 1) {
						for (int i = 1; i < values.size(); i++) {
							Map<String, Object> param = new LinkedHashMap<>();
							param.put("id", values.get(i));
							attr.put("child", param);
						}
					}
				}
				fields.put(fieldName, attr);
			} else if (StringUtils.equalsIgnoreCase(item.getType(), PlatformCustomFieldType.RICH_TEXT.name())) {
				String parseText = parseMsRichTextToJira(fieldName, item.getValue().toString(), request.getRichFileMap(), platformCustomFieldMap, remainImgNameFromRichText);
				if (fieldName.equals(JiraMetadataField.DESCRIPTION_FIELD_NAME)) {
					platformBug.setPlatformDescription(platformCustomFieldMap.get(fieldName));
				}
				fields.put(fieldName, parseText);
			} else if (StringUtils.equalsIgnoreCase(item.getType(), PlatformCustomFieldType.DATETIME.name())) {
				if (item.getValue() instanceof String) {
					// 日期时间类型处理成Jira可解析{2023-07-12 11:12:46 -> 2021-12-10T11:12:46+08:00}
					fields.put(fieldName, ((String) item.getValue()).trim().replace(" ", "T") + "+08:00");
				}
			} else if (StringUtils.equalsIgnoreCase(item.getType(), PlatformCustomFieldType.MULTIPLE_INPUT.name())) {
				List<Object> values = PluginUtils.parseArray((String) item.getValue(), Object.class);
				fields.put(fieldName, values);
			} else {
				if (StringUtils.equalsIgnoreCase(fieldName, JiraMetadataField.SUMMARY_FIELD_NAME)) {
					// summary -> ms_title
					platformBug.setPlatformTitle(item.getValue().toString());
				}
				fields.put(fieldName, item.getValue());
			}
		}

		// 如果平台自定义字段中不包含Jira的Summary事务, 手动插入
		if (!fields.containsKey(JiraMetadataField.SUMMARY_FIELD_NAME)) {
			fields.put(JiraMetadataField.SUMMARY_FIELD_NAME, request.getTitle());
		}

		// 如果平台自定义字段中不包含Jira的描述字段, 手动插入
		if (!fields.containsKey(JiraMetadataField.DESCRIPTION_FIELD_NAME)) {
			String parseText = parseMsRichTextToJira(JiraMetadataField.DESCRIPTION_FIELD_NAME, request.getDescription(), request.getRichFileMap(), platformCustomFieldMap, remainImgNameFromRichText);
			fields.put(JiraMetadataField.DESCRIPTION_FIELD_NAME, parseText);
			platformBug.setPlatformDescription(platformCustomFieldMap.get(JiraMetadataField.DESCRIPTION_FIELD_NAME));
		}
		platformBug.setPlatformCustomFieldMap(platformCustomFieldMap);
	}

	/**
	 * 处理特殊字段
	 *
	 * @param fields 字段集合
	 */
	private void setSpecialParam(Map<String, Object> fields) {
		try {
			List<JiraCreateMetaField.Field> metaFields = jiraClient.getCreateMetadata(projectConfig.getJiraKey(), projectConfig.getJiraBugTypeId());
			for (JiraCreateMetaField.Field item : metaFields) {
				JiraCreateMetaField.Schema schema = item.getSchema();

				if (StringUtils.equals(item.getFieldId(), JiraMetadataSpecialSystemField.TIME_TRACKING)) {
					if (fields.get(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID) != null || fields.get(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID) != null) {
						// 原始预估, 时间追踪有值时, 封装timetracking
						Map<String, Object> newField = new LinkedHashMap<>();
						// originalEstimate -> 2d 转成 timetracking : { originalEstimate: 2d}
						newField.put(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID, fields.get(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID));
						newField.put(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID, fields.get(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID));
						fields.put(item.getFieldId(), newField);
					}
					fields.remove(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID);
					fields.remove(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID);
				}
				if (schema == null || fields.get(item.getFieldId()) == null) {
					continue;
				}
				if (schema.getCustom() != null) {
					if (schema.getCustom().endsWith(JiraMetadataSpecialCustomField.SPRINT_FIELD_NAME)) {
						Map field = (Map) fields.get(item.getFieldId());
						Object id = field.get("id");
						if (id != null) {
							// sprint 传参数比较特殊，需要要传数值
							fields.put(item.getFieldId(), Integer.parseInt(id.toString()));
						}
					} else if (schema.getCustom().endsWith("pic-link")) {
						// 特殊Link
						Map field = (Map) fields.get(item.getFieldId());
						fields.put(item.getFieldId(), field.get("id"));
					} else if (schema.getCustom().endsWith("multiuserpicker")) {
						// 多选用户列表
						List<Map> userItems = (List) fields.get(item.getFieldId());
						userItems.forEach(i -> {
							i.put("name", i.get("id"));
							i.put("id", i.get("id"));
						});
					}
				}
				if (schema.getType() != null) {
					if (schema.getType().endsWith("user")) {
						Map field = (Map) fields.get(item.getFieldId());
						// 如果不是用户ID，则是用户的key，参数调整为key
						Map newField = new LinkedHashMap<>();
						// name 是私有化部署使用
						newField.put("name", field.get("id").toString());
						// id 是SaaS使用
						newField.put("id", field.get("id").toString());
						fields.put(item.getFieldId(), newField);
					}
				}
			}
		} catch (Exception e) {
			PluginLogUtils.error(e);
			throw new MSPluginException("解析Jira特殊参数失败, 请检查参数: " + e.getMessage());
		}
	}

	/**
	 * 获取字段名称集合{错误信息展示需要}
	 *
	 * @param request 请求参数
	 * @return 字段名称集合
	 */
	private static Map<String, String> getFieldNameMap(PlatformBugUpdateRequest request) {
		Map<String, String> filedNameMap = null;
		if (!CollectionUtils.isEmpty(request.getCustomFieldList())) {
			filedNameMap = request.getCustomFieldList().stream()
					.collect(Collectors.toMap(PlatformCustomFieldItemDTO::getCustomData, PlatformCustomFieldItemDTO::getName));
		}
		return filedNameMap;
	}

	/**
	 * 根据字段类型, 匹配具体的字段集合
	 *
	 * @param specialFields 特殊字段集合
	 * @param type          字段类型
	 * @return true: 匹配到特殊字段, false: 未匹配到特殊字段
	 */
	private static boolean mappingSpecialField(Set<String> specialFields, String type) {
		// 匹配特殊字段, 包含即可
		Optional<String> findField = specialFields.stream().filter(field -> StringUtils.contains(type, field)).findAny();
		return findField.isPresent();
	}

	/**
	 * link jira issue
	 *
	 * @param issueLinkFields [0] issue link type, [1] issue link
	 * @param issueKey        issue key
	 * @param issueLinkTypes  issue link types
	 */
	private void linkIssue(List<PlatformCustomFieldItemDTO> issueLinkFields, String issueKey, List<JiraIssueLinkTypeResponse.IssueLinkType> issueLinkTypes) {
		// 暂时只支持关联一组事务, 前台Form表单对多组事务关联关系的支持麻烦
		PlatformCustomFieldItemDTO issueLinkType = issueLinkFields.get(0);
		PlatformCustomFieldItemDTO issueLink = issueLinkFields.get(1);
		if (issueLinkType.getValue() != null && issueLink.getValue() != null && StringUtils.isNotEmpty(issueLinkType.getValue().toString())) {
			String type = issueLinkType.getValue().toString();
			JiraIssueLinkTypeResponse.IssueLinkType attachType = issueLinkTypes.stream().filter(item -> StringUtils.equalsAny(type, item.getInward(), item.getOutward())).findFirst().get();
			List<String> linkKeys = PluginUtils.parseArray(issueLink.getValue().toString(), String.class);
			if (CollectionUtils.isEmpty(linkKeys)) {
				return;
			}
			linkKeys.forEach(linkKey -> {
				JiraIssueLinkRequest issueLinkRequest = new JiraIssueLinkRequest();
				issueLinkRequest.setType(JiraIssueLinkRequest.JiraIssueLinkType.builder().id(attachType.getId()).build());
				if (StringUtils.equals(type, attachType.getInward())) {
					issueLinkRequest.setInwardIssue(JiraIssueLinkRequest.JiraIssueLinkKey.builder().key(linkKey).build());
					issueLinkRequest.setOutwardIssue(JiraIssueLinkRequest.JiraIssueLinkKey.builder().key(issueKey).build());
				} else {
					issueLinkRequest.setOutwardIssue(JiraIssueLinkRequest.JiraIssueLinkKey.builder().key(linkKey).build());
					issueLinkRequest.setInwardIssue(JiraIssueLinkRequest.JiraIssueLinkKey.builder().key(issueKey).build());
				}
				jiraClient.linkIssue(issueLinkRequest);
			});
		}
	}

	/**
	 * un-link jira issue
	 *
	 * @param issueKey issue-key
	 */
	private void unLinkIssue(String issueKey) {
		JiraIssue issue = jiraClient.getIssues(issueKey);
		Map<String, Object> fields = issue.getFields();
		if (fields.containsKey(JiraMetadataSpecialSystemField.ISSUE_LINKS)) {
			List<Map<String, Object>> issueLinks = (List) fields.get(JiraMetadataSpecialSystemField.ISSUE_LINKS);
			issueLinks.stream().map(item -> item.get("id").toString()).forEach(jiraClient::unLinkIssue);
		}
	}

	/**
	 * 准备特定的选项数据, 防止多次网络请求
	 *
	 * @return 选项数据映射集合
	 */
	private Map<String, String> prepareOptionData() {
		Map<String, String> optionData = new HashMap<>();
		// Jira用户下拉选项
		optionData.put(JiraOptionKey.USER.name(), PluginUtils.toJSONString(getUserSearchOptions(StringUtils.EMPTY)));
		// Jira自定义Sprint选项
		List<JiraSprint> sprints = jiraClient.getSprint(null);
		List<SelectOption> sprintOptions = new ArrayList<>();
		sprints.forEach(sprint -> sprintOptions.add(new SelectOption(StringUtils.join(sprint.getName(), " (", sprint.getBoardName(), ")"), sprint.getId().toString())));
		optionData.put(JiraOptionKey.SPRINT.name(), PluginUtils.toJSONString(sprintOptions));
		// Jira自定义Epic选项
		List<JiraEpic> epics = jiraClient.getEpics(projectConfig.getJiraKey());
		List<SelectOption> epicOptions = new ArrayList<>();
		epics.forEach(epic -> epicOptions.add(new SelectOption(epic.getName(), epic.getKey())));
		optionData.put(JiraOptionKey.EPIC.name(), PluginUtils.toJSONString(epicOptions));
		// 获取Jira指派人选项
		optionData.put(JiraOptionKey.ASSIGN.name(), PluginUtils.toJSONString(getAssignableOptions(projectConfig.getJiraKey(), null)));
		// 获取Jira关联事务选项
		optionData.put(JiraOptionKey.ISSUE_LINK.name(), PluginUtils.toJSONString(getIssueLinkOptions(null, null)));
		optionData.put(JiraOptionKey.ISSUE_LINK_TYPE.name(), PluginUtils.toJSONString(getIssueLinkTypeOptions()));
		return optionData;
	}

	/**
	 * 同步Jira字段到平台缺陷字段
	 *
	 * @param msBug                 平台缺陷
	 * @param jiraIssue             jira缺陷
	 * @param defaultTemplateFields 默认模板字段集合
	 */
	private void syncJiraFieldToMsBug(PlatformBugDTO msBug, JiraIssue jiraIssue, List<PlatformCustomFieldItemDTO> defaultTemplateFields, Map<String, String> jiraIssueAttachmentMap) {
		try {
			// 下载的富文本文件集合
			Map<String, String> richFileMap = new HashMap<>(16);
			// 处理基础字段
			parseBaseFieldToMsBug(msBug, jiraIssue.getFields(), jiraIssueAttachmentMap, richFileMap);
			// 处理自定义字段
			parseCustomFieldToMsBug(msBug, jiraIssue.getFields(), defaultTemplateFields, jiraIssueAttachmentMap, richFileMap);
			// 设置富文本集合
			msBug.setRichTextImageMap(richFileMap);
		} catch (Exception e) {
			PluginLogUtils.error(e);
		}
	}

	/**
	 * 解析基础字段到平台缺陷字段
	 *
	 * @param msBug        平台缺陷
	 * @param jiraFieldMap jira字段集合
	 * @throws Exception 解析异常
	 */
	private void parseBaseFieldToMsBug(PlatformBugDTO msBug, Map jiraFieldMap, Map<String, String> jiraIssueAttachmentMap, Map<String, String> richFileMap) throws Exception {
		// 处理基础字段(TITLE, DESCRIPTION, HANDLE_USER, STATUS)
		msBug.setTitle(jiraFieldMap.get(JiraMetadataField.SUMMARY_FIELD_NAME).toString());
		if (jiraFieldMap.get(JiraMetadataField.DESCRIPTION_FIELD_NAME) == null) {
			msBug.setDescription(null);
		} else {
			msBug.setDescription(parseJiraRichTextToMs(jiraFieldMap.get(JiraMetadataField.DESCRIPTION_FIELD_NAME).toString(), msBug, jiraIssueAttachmentMap, richFileMap));
		}
		Map<String, Object> assignMap = (Map) jiraFieldMap.get(JiraMetadataSpecialSystemField.ASSIGNEE);
		if (assignMap == null) {
			msBug.setHandleUser(StringUtils.EMPTY);
		} else {
			String assignUser;
			if (assignMap.containsKey("accountId")) {
				assignUser = assignMap.get("accountId").toString();
			} else if (assignMap.containsKey("key")) {
				assignUser = assignMap.get("key").toString();
			} else {
				assignUser = StringUtils.EMPTY;
			}
			if (!StringUtils.equals(msBug.getHandleUser(), assignUser)) {
				msBug.setHandleUser(assignUser);
				msBug.setHandleUsers(StringUtils.isBlank(msBug.getHandleUsers()) ? assignUser : msBug.getHandleUsers() + "," + assignUser);
			}
		}
		msBug.setStatus(getFieldStatus(jiraFieldMap));
		msBug.setCreateUser("admin");
		msBug.setUpdateUser("admin");
		msBug.setCreateTime(sdfWithZone.parse((String) jiraFieldMap.get("created")).getTime());
		msBug.setUpdateTime(sdfWithZone.parse((String) jiraFieldMap.get("updated")).getTime());
	}

	/**
	 * 解析自定义字段到平台缺陷字段
	 *
	 * @param msBug                 平台缺陷
	 * @param jiraFieldMap          jira字段集合
	 * @param defaultTemplateFields 默认模板字段集合
	 */
	private void parseCustomFieldToMsBug(PlatformBugDTO msBug, Map jiraFieldMap, List<PlatformCustomFieldItemDTO> defaultTemplateFields,
										 Map<String, String> jiraIssueAttachmentMap, Map<String, String> richFileMap) {
		List<PlatformCustomFieldItemDTO> needSyncCustomFields = new ArrayList<>();
		if (isSupportDefaultTemplate() && msBug.getPlatformDefaultTemplate()) {
			// 缺陷使用的平台默认模板, 使用平台默认模板字段
			for (PlatformCustomFieldItemDTO field : defaultTemplateFields) {
				needSyncCustomFields.add(SerializationUtils.clone(field));
			}
		} else {
			// 缺陷使用的非平台默认模板, 使用模板中配置的映射字段
			for (PlatformCustomFieldItemDTO field : msBug.getNeedSyncCustomFields()) {
				needSyncCustomFields.add(SerializationUtils.clone(field));
			}
		}
		if (CollectionUtils.isEmpty(needSyncCustomFields)) {
			return;
		}
		needSyncCustomFields.forEach(fieldItem -> {
			Object value = jiraFieldMap.get(fieldItem.getCustomData());
			if (value != null) {
				if (value instanceof Map) {
					fieldItem.setValue(getSyncJsonParamValue(value));
				} else if (value instanceof List) {
					if (CollectionUtils.isEmpty((List) value)) {
						fieldItem.setValue(null);
					} else {
						if (StringUtils.equals(fieldItem.getType(), PlatformCustomFieldType.SELECT.name())) {
							fieldItem.setValue(getSyncJsonParamValue(((List) value).get(0)));
						} else {
							List<Object> values = new ArrayList<>();
							((List) value).forEach(attr -> {
								if (attr instanceof Map) {
									values.add(getSyncJsonParamValue(attr));
								} else {
									values.add(attr);
								}
							});
							fieldItem.setValue(PluginUtils.toJSONString(values));
						}
					}
				} else {
					// 富文本需单独处理
					if (StringUtils.equals(fieldItem.getType(), PlatformCustomFieldType.RICH_TEXT.name())) {
						if (!StringUtils.equals(fieldItem.getCustomData(), JiraMetadataField.DESCRIPTION_FIELD_NAME)) {
							fieldItem.setValue(parseJiraRichTextToMs(value.toString(), msBug, jiraIssueAttachmentMap, richFileMap));
						} else {
							fieldItem.setValue(msBug.getDescription());
						}
					} else {
						fieldItem.setValue(value.toString());
					}
				}
			} else {
				fieldItem.setValue(null);
			}
		});
		parseSpecialFieldToMsBug(jiraFieldMap, needSyncCustomFields);
		msBug.setCustomFieldList(needSyncCustomFields);
	}

	/**
	 * 只处理需要同步的特殊字段
	 *
	 * @param jiraFieldMap         jira缺陷字段集合
	 * @param needSyncCustomFields 需要同步的自定义字段
	 */
	private void parseSpecialFieldToMsBug(Map jiraFieldMap, List<PlatformCustomFieldItemDTO> needSyncCustomFields) {
		needSyncCustomFields.forEach(fieldItem -> {
			Object value = jiraFieldMap.get(fieldItem.getCustomData());
			// user select
			if (BooleanUtils.isTrue(fieldItem.getSupportSearch()) && StringUtils.equalsAny(fieldItem.getSearchMethod(),
					JiraMetadataFieldSearchMethod.GET_USER, JiraMetadataFieldSearchMethod.GET_ASSIGNABLE)) {
				if (value == null) {
					fieldItem.setValue(null);
				} else {
					if (value instanceof List) {
						List<String> values = new ArrayList<>();
						for (Object item : ((List) value)) {
							Map itemMap = (Map) item;
							String val;
							Object accountId = itemMap.get("accountId");
							if (accountId != null && StringUtils.isNotBlank(accountId.toString())) {
								val = accountId.toString();
							} else {
								val = itemMap.get("name").toString();
							}
							values.add(val);
						}
						fieldItem.setValue(PluginUtils.toJSONString(values));
					} else {
						Map itemMap = (Map) value;
						Object accountId = itemMap.get("accountId");
						if (accountId != null && StringUtils.isNotBlank(accountId.toString())) {
							fieldItem.setValue(accountId.toString());
						} else {
							fieldItem.setValue(itemMap.get("name").toString());
						}
					}
				}
			}
			// datetime
			if (StringUtils.equals(fieldItem.getType(), PlatformCustomFieldType.DATETIME.name())) {
				if (value != null && value instanceof String) {
					fieldItem.setValue(convertToUniversalFormat(value.toString()));
				} else {
					fieldItem.setValue(null);
				}
			}
			// time tracking
			if (StringUtils.equals(fieldItem.getId(), JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID)) {
				Map timeTracking = (Map) jiraFieldMap.get(JiraMetadataSpecialSystemField.TIME_TRACKING);
				if (!CollectionUtils.isEmpty(timeTracking)) {
					fieldItem.setValue(timeTracking.get(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID).toString());
				}
			}
			if (StringUtils.equals(fieldItem.getId(), JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID)) {
				Map timeTracking = (Map) jiraFieldMap.get(JiraMetadataSpecialSystemField.TIME_TRACKING);
				if (!CollectionUtils.isEmpty(timeTracking)) {
					fieldItem.setValue(timeTracking.get(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID).toString());
				}
			}
			// issue link
			if (StringUtils.equals(fieldItem.getId(), JiraMetadataField.ISSUE_LINK_TYPE)) {
				Map<String, String> linkIssueMap = parseIssueLink(jiraFieldMap.get(JiraMetadataSpecialSystemField.ISSUE_LINKS));
				if (!CollectionUtils.isEmpty(linkIssueMap)) {
					fieldItem.setValue(linkIssueMap.get(JiraMetadataField.ISSUE_LINK_TYPE));
					Optional<PlatformCustomFieldItemDTO> any = needSyncCustomFields.stream().filter(field -> StringUtils.equals(field.getId(), JiraMetadataSpecialSystemField.ISSUE_LINKS)).findAny();
					any.ifPresent(platformCustomFieldItemDTO -> platformCustomFieldItemDTO.setValue(linkIssueMap.get(JiraMetadataSpecialSystemField.ISSUE_LINKS)));
				}
			}
		});
	}

	/**
	 * 解析附件到平台缺陷
	 *
	 * @param result    同步缺陷结果
	 * @param msBug     平台缺陷
	 * @param jiraIssue jira缺陷
	 */
	private void parseAttachmentToMsBug(SyncBugResult result, PlatformBugDTO msBug, Map<String, String> jiraIssueAttachmentMap) {
		try {
			// handle bug attachment
			if (!CollectionUtils.isEmpty(jiraIssueAttachmentMap)) {
				Map<String, List<PlatformAttachment>> attachmentMap = result.getAttachmentMap();
				attachmentMap.put(msBug.getId(), new ArrayList<>());
				jiraIssueAttachmentMap.keySet().forEach(key -> {
					PlatformAttachment syncAttachment = new PlatformAttachment();
					// name 用于查重
					syncAttachment.setFileName(key);
					// key 用于获取附件内容
					syncAttachment.setFileKey(jiraIssueAttachmentMap.get(key));
					attachmentMap.get(msBug.getId()).add(syncAttachment);
				});
			} else {
				result.getAttachmentMap().put(msBug.getId(), new ArrayList<>());
			}
		} catch (Exception e) {
			PluginLogUtils.error(e);
			throw new MSPluginException(e);
		}
	}

	/**
	 * 获取字段状态
	 *
	 * @param fields 字段集合
	 * @return 状态
	 */
	private String getFieldStatus(Map fields) {
		Map statusObj = (Map) fields.get("status");
		if (statusObj != null) {
			Map statusCategory = (Map) statusObj.get("statusCategory");
			return statusObj.get("id").toString() == null ? statusCategory.get("id").toString() : statusObj.get("id").toString();
		}
		return "";
	}

	/**
	 * 获取同步的JSON值
	 *
	 * @param value 对象值
	 * @return JSON字符串
	 */
	private String getSyncJsonParamValue(Object value) {
		Map valObj = ((Map) value);
		Map child = (Map) valObj.get("child");
		String idValue = Optional.ofNullable(valObj.get("id")).orElse(StringUtils.EMPTY).toString();

		if (child != null) {// 级联框
			return PluginUtils.toJSONString(getCascadeValues(idValue, child));
		} else {
			if (StringUtils.isNotBlank(idValue)) {
				return idValue;
			} else {
				return valObj.get("key") == null ? null : valObj.get("key").toString();
			}
		}
	}

	/**
	 * 获取级联框的值
	 *
	 * @param idValue id值
	 * @param child   子级元素值
	 * @return 级联框的值
	 */
	private List<Object> getCascadeValues(String idValue, Map child) {
		List<Object> values = new ArrayList<>();
		if (StringUtils.isNotBlank(idValue)) {
			values.add(idValue);
		}
		if (child.get("id") != null && StringUtils.isNotBlank(child.get("id").toString())) {
			values.add(child.get("id"));
		}
		return values;
	}

	/**
	 * 转换日期至成通用的日期格式
	 *
	 * @param input 日期字符串
	 * @return 通用的日期
	 */
	private static String convertToUniversalFormat(String input) {
		if (!input.contains("T")) {
			return input;
		}
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

		OffsetDateTime offsetDateTime = OffsetDateTime.parse(input, inputFormatter);
		return offsetDateTime.format(outputFormatter);
	}

	/**
	 * 解析 issue-link
	 *
	 * @param value 对象值
	 * @return issue-link
	 */
	private Map<String, String> parseIssueLink(Object value) {
		Map<String, String> linkIssueResultMap = new HashMap<>();
		List<Map<String, Object>> issueLinks = (List) value;
		if (!CollectionUtils.isEmpty(issueLinks)) {
			Map<String, Object> firstLink = issueLinks.get(0);
			Map<String, Object> linkType = (Map) firstLink.get("type");
			boolean isFirstInward = firstLink.containsKey("inwardIssue");
			String type = isFirstInward ? linkType.get("inward").toString() : linkType.get("outward").toString();
			linkIssueResultMap.put(JiraMetadataField.ISSUE_LINK_TYPE, type);
			List<String> issueLinkVars = new ArrayList<>();
			issueLinks.stream().filter(filterLink -> {
						Map<String, Object> filterLinkType = (Map) filterLink.get("type");
						return StringUtils.equals(filterLinkType.get("id").toString(), linkType.get("id").toString());
					}).filter(filterLink -> isFirstInward ? filterLink.containsKey("inwardIssue") : filterLink.containsKey("outwardIssue"))
					.forEach(filterLink -> {
						Map<String, Object> linkIssue;
						if (isFirstInward) {
							linkIssue = (Map) filterLink.get("inwardIssue");
						} else {
							linkIssue = (Map) filterLink.get("outwardIssue");
						}
						issueLinkVars.add(linkIssue.get("key").toString());
					});
			linkIssueResultMap.put(JiraMetadataSpecialSystemField.ISSUE_LINKS, PluginUtils.toJSONString(issueLinkVars));
		}
		return linkIssueResultMap;
	}

	/**
	 * 解析状态Transition值
	 *
	 * @param transitions 状态Transition集合
	 * @param status      状态值
	 * @return 状态值
	 */
	private String parseTransitionStatus(List<JiraTransitionsResponse.Transitions> transitions, String status) {
		if (!CollectionUtils.isEmpty(transitions)) {
			JiraTransitionsResponse.Transitions transition = transitions.stream().filter(item -> StringUtils.equals(item.getTo().getId(),
					status)).findFirst().orElse(null);
			if (transition != null) {
				return transition.getId();
			}
		}
		return status;
	}

	/**
	 * 解析MS富文本字段内容至Jira富文本内容
	 *
	 * <p style="">
	 * <img src="https://pic.com/bjh/1.jpeg" permalinksrc="https://pic.com/bjh/1.jpeg">
	 * <img src="/bug/attachment/preview/md/pid/fid/true" fileid="884436848394240" permalinksrc="/bug/attachment/preview/md/pid/fid/true">
	 * <img src="/bug/attachment/preview/md/pid/fid/true" psrc="!image-20202020-32393131.jpg!">
	 * </p>
	 *
	 * @param key                       富文本字段Key, 用来MS拦截并替换字段内容
	 * @param content                   解析内容, 格式参考如上
	 * @param msFileMap                 MS上传的一些图片临时文件集合
	 * @param platformBug               平台缺陷
	 * @param remainImgNameFromRichText 遗留的图片附件名称集合(同步附件时, 用来做排除)
	 * @return 解析后的Jira富文本内容 (图片默认样式: width=1360,height=876)
	 */
	private String parseMsRichTextToJira(String key, String content, Map<String, File> msFileMap, Map<String, String> platformCustomFieldMap, List<String> remainImgNameFromRichText) {
		if (StringUtils.isBlank(content)) {
			return null;
		}
		String sourceRegex = "(<img src=\"" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX + "/)(.*?)(\")";
		StringBuilder jiraRichText = new StringBuilder();
		String[] splitText = content.split(">");
		for (String split : splitText) {
			String replaceText = "";
			if (split.contains("<img") && split.contains("http")) {
				// ms-httpUrl: <img src="http-url"> => !http-url|width=!
				String msHttpUrl = split.substring(split.indexOf("permalinksrc=") + 13).replaceAll("\"", StringUtils.EMPTY).replaceAll(StringUtils.SPACE, StringUtils.EMPTY);
				replaceText = "!" + msHttpUrl + "|width=1360,height=876!";
			} else if (split.contains("<img") && !split.contains("http")) {
				// ms-localUrl: <img src="local-url" psrc="!jira-pic-name!">
				// local-url match by source-regex
				if (split.contains("psrc")) {
					// include psrc, split it then append the jira text
					String jiraLocalUrl = split.substring(split.indexOf("psrc=") + 5).replaceAll("\"", StringUtils.EMPTY).replaceAll(StringUtils.SPACE, StringUtils.EMPTY);
					Matcher matcher = Pattern.compile(sourceRegex).matcher(split);
					while (matcher.find()) {
						String group = matcher.group(0);
						String msLocalUrl = group.substring(group.indexOf("<img src=") + 9).replaceAll("\"", StringUtils.EMPTY).replaceAll(StringUtils.SPACE, StringUtils.EMPTY);
						replaceText = "!" + jiraLocalUrl + "|width=1360,height=876,alt=\'" + msLocalUrl + "\'!";
						remainImgNameFromRichText.add(jiraLocalUrl);
						if (split.contains("fileid")) {
							msFileMap.remove(split.substring(split.indexOf("fileid") + 7, split.indexOf("permalinksrc=")).replaceAll("\"", StringUtils.EMPTY).trim());
						}
					}
				} else {
					// ms-localUrl => !image-20240319-113551.png|alt=!
					String msLocalUrl = split.substring(split.indexOf("permalinksrc=") + 13).replaceAll("\"", StringUtils.EMPTY);
					if (split.contains("fileid")) {
						String fileId = split.substring(split.indexOf("fileid") + 7, split.indexOf("permalinksrc="))
								.replaceAll("\"", StringUtils.EMPTY).replaceAll(StringUtils.SPACE, StringUtils.EMPTY);
						if (msFileMap.containsKey(fileId)) {
							// rename ms image to jira  (rules: image-20201015-110015-uid.jpg)
							String fileName = "image-" + UUID.randomUUID().toString() + ".jpg";
							File sourceFile = msFileMap.get(fileId);
							File targetFile = new File(sourceFile.getParent(), fileName);
							sourceFile.renameTo(targetFile);
							msFileMap.put(fileId, targetFile);
							replaceText = "!" + fileName + "|width=1360,height=876,alt=\'" + msLocalUrl + "\'!";
							content = content.replaceAll(split, split + " psrc=\"" + fileName + "\"");
						}
					}
				}
			} else {
				replaceText = split + ">";
			}
			jiraRichText.append(replaceText);
		}
		platformCustomFieldMap.put(key, content);
		return jiraRichText.toString();
	}

	/**
	 * 解析Jira富文本内容至MS
	 *
	 * <p style="">
	 * !https://pic.com/1.jpeg|width=1360,height=876!
	 * !image-202020-173612.jpg|width=1360,height=876,alt='/bug/attachment/preview/md/pid/fid/true'!
	 * </p>
	 *
	 * @param content                解析内容, 格式参考如上
	 * @param msBug                  MS缺陷
	 * @param jiraIssueAttachmentMap Jira缺陷集合(用来排除图片资源附件, 防止同步)
	 * @return
	 */
	private String parseJiraRichTextToMs(String content, PlatformBugDTO msBug, Map<String, String> jiraIssueAttachmentMap, Map<String, String> richFileMap) {
		if (StringUtils.isBlank(content)) {
			return null;
		}
		StringBuilder msRichText = new StringBuilder();
		String[] splitText = content.split("!(.*?)");
		for (String split : splitText) {
			String replaceText = "";
			if (split.startsWith("http")) {
				// jiraHttpUrl: !https://pic.com/1.jpeg|width=1360,height=876! => <img src="http-url">
				String httpSrcUrl = split.split("\\|")[0];
				replaceText = "<img src=\"" + httpSrcUrl + "\" permalinksrc=\"" + httpSrcUrl + "\" >";
			} else if (split.contains("|width")) {
				// jiraLocalUrl => msLocalUrl
				// !image-202020-173612.jpg|width=1360,height=876,alt='/bug/attachment/preview/md/pid/fid/true'!
				// !image-202020-173612.jpg|width=1360,height=876
				String localJiraSrcUrl = split.split("\\|")[0];
				String fileKey = "";
				if (jiraIssueAttachmentMap.containsKey(localJiraSrcUrl)) {
					fileKey = jiraIssueAttachmentMap.get(localJiraSrcUrl);
					jiraIssueAttachmentMap.remove(localJiraSrcUrl);
				}
				String localMsSrcUrl = split.split("\\|")[1];
				if (StringUtils.contains(localMsSrcUrl, "alt=\"\'" + MS_RICH_TEXT_PREVIEW_SRC_PREFIX)) {
					String altLocalMsUrl = localMsSrcUrl.substring(localMsSrcUrl.indexOf("alt=") + 4)
							.replaceAll("\'", StringUtils.EMPTY).replaceAll("\"", StringUtils.EMPTY);
					replaceText = "<img src=\"" + altLocalMsUrl + "\" psrc=\"" + localJiraSrcUrl + "\" >";
				} else {
					// set field key to alt, for ms download and replace it
					replaceText = "<img alt=\"" + fileKey + "\" psrc=\"" + localJiraSrcUrl + "\" >";
					richFileMap.put(fileKey, localJiraSrcUrl);
				}
			} else {
				replaceText = split;
			}
			msRichText.append(replaceText);
		}
		return msRichText.toString();
	}
}
