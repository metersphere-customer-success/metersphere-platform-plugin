package io.metersphere.plugin.tapd.client;

import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.spi.BaseClient;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import io.metersphere.plugin.tapd.constants.TapdSystemType;
import io.metersphere.plugin.tapd.constants.TapdUrl;
import io.metersphere.plugin.tapd.domain.TapdIntegrationConfig;
import io.metersphere.plugin.tapd.domain.TapdProject;
import io.metersphere.plugin.tapd.domain.response.TapdBaseResponse;
import io.metersphere.plugin.tapd.domain.response.TapdStoryResponse;
import io.metersphere.plugin.tapd.domain.response.TapdTransitionStatusItem;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TapdClient extends BaseClient {

	protected static String ENDPOINT = "https://api.tapd.cn";

	protected static String BASE_URL = "https://www.tapd.cn";

	protected static String USERNAME;

	protected static String PASSWORD;

	public TapdClient(TapdIntegrationConfig integrationConfig) {
		initConfig(integrationConfig);
	}

	/**
	 * 初始化配置参数
	 *
	 * @param config 配置
	 */
	public void initConfig(TapdIntegrationConfig config) {
		if (config == null) {
			throw new MSPluginException("Tapd服务集成配置为空");
		}
		USERNAME = config.getAccount();
		PASSWORD = config.getPassword();
	}

	/**
	 * 认证
	 */
	public void auth() {
		try {
			restTemplate.exchange(ENDPOINT + TapdUrl.AUTH, HttpMethod.GET, getAuthHttpEntity(), String.class);
		} catch (Exception e) {
			if (e instanceof HttpClientErrorException && ((HttpClientErrorException) e).getStatusCode().is4xxClientError()) {
				throw new MSPluginException("TAPD认证失败: API账号或口令错误");
			} else {
				PluginLogUtils.error(e);
				throw new MSPluginException("TAPD认证失败: 请求错误");
			}
		}
	}

	/**
	 * 获取项目
	 *
	 * @param projectKey 项目key
	 * @return 返回项目
	 */
	public TapdProject getProject(String projectKey) {
		try {
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.GET_PROJECT_INFO, HttpMethod.GET, getAuthHttpEntity(), TapdBaseResponse.class, projectKey);
			if (response.getBody() == null) {
				return null;
			}
			return PluginUtils.parseObject(PluginUtils.toJSONString(((Map) response.getBody().getData()).get("Workspace")), TapdProject.class);
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 获取起始状态流
	 *
	 * @param systemType 系统类型
	 * @param projectKey 项目ID
	 * @return
	 */
	public SelectOption getFirstStepWorkFlow(String systemType, String projectKey, String storyTypeId) {
		SelectOption statusOption = new SelectOption();
		try {
			String firstStepUrl = ENDPOINT + TapdUrl.GET_WORKFLOW_FIRST_STEP;
			if (StringUtils.equals(TapdSystemType.STORY, systemType)) {
				firstStepUrl = firstStepUrl + "&workitem_type_id=" + storyTypeId;
			}
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(firstStepUrl, HttpMethod.GET, getAuthHttpEntity(), TapdBaseResponse.class, systemType, projectKey);
			if (response.getBody() == null) {
				return null;
			}
			Map<String, String> statusMap = PluginUtils.parseMap(PluginUtils.toJSONString(response.getBody().getData()));
			statusMap.keySet().forEach(statusKey -> {
				statusOption.setText(statusMap.get(statusKey));
				statusOption.setValue(statusKey);
			});
			return statusOption;
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 获取状态流, 流转细则
	 *
	 * @param systemType 系统类型
	 * @param projectKey 项目ID
	 * @return
	 */
	public List<SelectOption> getWorkFlowTransition(String systemType, String projectKey, String storyTypeId, String previousStatus) {
		List<SelectOption> statusOption = new ArrayList<>();
		try {
			String getTransitionUrl = ENDPOINT + TapdUrl.GET_WORKFLOW_TRANSITIONS;
			if (StringUtils.equals(TapdSystemType.STORY, systemType)) {
				getTransitionUrl = getTransitionUrl + "&workitem_type_id=" + storyTypeId;
			}
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.GET_WORKFLOW_TRANSITIONS, HttpMethod.GET, getAuthHttpEntity(), TapdBaseResponse.class, systemType, projectKey);
			if (response.getBody() == null) {
				return null;
			}
			List<TapdTransitionStatusItem> statusTransitions = PluginUtils.parseArray(PluginUtils.toJSONString(response.getBody().getData()), TapdTransitionStatusItem.class);
			if (CollectionUtils.isEmpty(statusTransitions)) {
				return null;
			}
			// 获取工作流状态中英文名对应关系
			ResponseEntity<TapdBaseResponse> statusDictResponse = restTemplate.exchange(ENDPOINT + TapdUrl.GET_WORKFLOW_STATUS_MAP, HttpMethod.GET, getAuthHttpEntity(), TapdBaseResponse.class, systemType, projectKey);
			Map<String, String> statusDictMap = PluginUtils.parseMap(PluginUtils.toJSONString(statusDictResponse.getBody().getData()));
			List<String> transitionStatus = new ArrayList<>();
			if (StringUtils.isNotBlank(previousStatus)) {
				transitionStatus = statusTransitions.stream().filter(transition -> StringUtils.equals(transition.getStepPrevious(), previousStatus)).map(TapdTransitionStatusItem::getStepNext).distinct().collect(Collectors.toList());
			} else {
				transitionStatus = statusTransitions.stream().map(TapdTransitionStatusItem::getStepPrevious).distinct().collect(Collectors.toList());
			}
			transitionStatus.forEach(statusKey -> {
				SelectOption selectOption = new SelectOption();
				selectOption.setText(statusDictMap.get(statusKey) == null ? statusKey : statusDictMap.get(statusKey));
				selectOption.setValue(statusKey);
				statusOption.add(selectOption);
			});
			return statusOption;
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	 * 获取项目成员列表
	 *
	 * @param projectKey
	 * @return
	 */
	public List<SelectOption> getProjectUsers(String projectKey) {
		List<SelectOption> userOptions = new ArrayList<>();
		try {
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.GET_PROJECT_USERS, HttpMethod.GET, getAuthHttpEntity(), TapdBaseResponse.class, projectKey);
			if (response.getBody() == null) {
				throw new MSPluginException("获取Tapd项目成员列表为空!");
			}
			List<Map> userMaps = PluginUtils.parseArray(PluginUtils.toJSONString(response.getBody().getData()), Map.class);
			if (CollectionUtils.isEmpty(userMaps)) {
				throw new MSPluginException("获取Tapd项目成员列表为空!");
			}
			userMaps.forEach(userMap -> {
				Map<String, String> user = PluginUtils.parseMap(PluginUtils.toJSONString(userMap.get("UserWorkspace")));
				SelectOption selectOption = new SelectOption();
				selectOption.setText(user.get("user"));
				selectOption.setValue(user.get("user"));
				userOptions.add(selectOption);
			});
			return userOptions;
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
			throw new MSPluginException("获取Tapd项目成员列表异常!");
		}
	}

	/**
	 * 分页获取项目的需求
	 *
	 * @param projectKey 项目Key
	 * @param startPage  开始页码
	 * @param pageSize   每页Size
	 * @return
	 */
	public List<TapdStoryResponse> getProjectStorys(String projectKey, Integer startPage, Integer pageSize) {
		List<TapdStoryResponse> storys = new ArrayList<>();
		try {
			ResponseEntity<TapdBaseResponse> response = restTemplate.exchange(ENDPOINT + TapdUrl.GET_PROJECT_STORY, HttpMethod.GET, getAuthHttpEntity(),
					TapdBaseResponse.class, projectKey, startPage, pageSize);
			if (response.getBody() == null) {
				return new ArrayList<>();
			}
			List<Map> storyMaps = PluginUtils.parseArray(PluginUtils.toJSONString(response.getBody().getData()), Map.class);
			List<TapdStoryResponse> tmpStorys = new ArrayList<>();
			storyMaps.forEach(storyMap -> {
				TapdStoryResponse story = PluginUtils.parseObject(PluginUtils.toJSONString(storyMap.get("Story")), TapdStoryResponse.class);
				tmpStorys.add(story);
			});
			List<TapdStoryResponse> childStorys = tmpStorys.stream().filter(story -> StringUtils.isBlank(story.getParent_id()) || !StringUtils.equals(story.getParent_id(), "0")).collect(Collectors.toList());
			List<TapdStoryResponse> parentStorys = tmpStorys.stream().filter(story -> StringUtils.isNotBlank(story.getParent_id()) && StringUtils.equals(story.getParent_id(), "0")).collect(Collectors.toList());
			Iterator<TapdStoryResponse> iterator = parentStorys.iterator();
			while (iterator.hasNext()) {
				TapdStoryResponse story = iterator.next();
				if (StringUtils.isNotBlank(story.getChildren_id()) && !StringUtils.equalsAny(story.getChildren_id(), "|", "||")) {
					List<String> childStoryIds = List.of(story.getChildren_id().replaceAll("\\|\\|", StringUtils.EMPTY).split("\\|"));
					List<TapdStoryResponse> filterChilds = childStorys.stream().filter(filterStory -> childStoryIds.contains(filterStory.getId())).collect(Collectors.toList());
					story.setChildren(filterChilds);
				}
				storys.add(story);
			}
		} catch (Exception e) {
			PluginLogUtils.error(e.getMessage(), e);
		}
		return storys;
	}

	/**
	 * 获取认证实体
	 *
	 * @return
	 */
	protected HttpEntity<MultiValueMap<String, String>> getAuthHttpEntity() {
		return new HttpEntity<>(getAuthHeader());
	}

	/**
	 * 获取认证Header Base64{api_user:api_password}
	 *
	 * @return
	 */
	protected HttpHeaders getAuthHeader() {
		return getBasicHttpHeaders(USERNAME, PASSWORD);
	}

	/**
	 * 获取URL前缀
	 *
	 * @return
	 */
	public String getBaseUrl() {
		return BASE_URL;
	}
}
