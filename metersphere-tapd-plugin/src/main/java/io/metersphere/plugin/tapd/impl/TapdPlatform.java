package io.metersphere.plugin.tapd.impl;

import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.dto.SyncBugResult;
import io.metersphere.plugin.platform.dto.request.*;
import io.metersphere.plugin.platform.dto.response.PlatformBugUpdateDTO;
import io.metersphere.plugin.platform.dto.response.PlatformCustomFieldItemDTO;
import io.metersphere.plugin.platform.dto.response.PlatformDemandDTO;
import io.metersphere.plugin.platform.spi.AbstractPlatform;
import io.metersphere.plugin.platform.utils.PluginPager;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import io.metersphere.plugin.tapd.client.TapdClient;
import io.metersphere.plugin.tapd.constants.TapdSystemType;
import io.metersphere.plugin.tapd.domain.TapdIntegrationConfig;
import io.metersphere.plugin.tapd.domain.TapdProject;
import io.metersphere.plugin.tapd.domain.TapdProjectConfig;
import io.metersphere.plugin.tapd.domain.TapdUserPlatformInfo;
import io.metersphere.plugin.tapd.domain.response.TapdStoryResponse;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.util.CollectionUtils;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author song-cc-rock
 */
@Extension
public class TapdPlatform extends AbstractPlatform {

	protected TapdClient tapdClient;

	public TapdPlatform(PlatformRequest request) {
		super(request);
		TapdIntegrationConfig config = getIntegrationConfig(request.getIntegrationConfig(), TapdIntegrationConfig.class);
		tapdClient = new TapdClient(config);
	}

	@Override
	public void validateIntegrationConfig() {
		tapdClient.auth();
	}

	@Override
	public void validateUserConfig(String userConfig) {
		TapdUserPlatformInfo platformConfig = PluginUtils.parseObject(userConfig, TapdUserPlatformInfo.class);
		if (StringUtils.isBlank(platformConfig.getTapdAccount()) && StringUtils.isBlank(platformConfig.getTapdPassword())) {
			throw new MSPluginException("TAPD认证失败: 账号或密码为空");
		}
		setUserConfig(userConfig, true);
		tapdClient.auth();
	}

	@Override
	public void validateProjectConfig(String projectConfigStr) {
		try {
			TapdProjectConfig projectConfig = getProjectConfig(projectConfigStr);
			if (StringUtils.isBlank(projectConfig.getTapdKey())) {
				throw new MSPluginException("TAPD项目校验参数不能为空!");
			}
			TapdProject project = tapdClient.getProject(projectConfig.getTapdKey());
			if (project == null || StringUtils.isBlank(project.getId())) {
				throw new MSPluginException("项目不存在");
			}
		} catch (Exception e) {
			throw new MSPluginException(e.getMessage());
		}
	}

	@Override
	public boolean isSupportDefaultTemplate() {
		// Tapd currently does not support default templates
		return false;
	}

	@Override
	public List<PlatformCustomFieldItemDTO> getDefaultTemplateCustomField(String projectConfig) {
		// when isSupportDefaultTemplate get true, implement this method;
		return null;
	}

	@Override
	public List<SelectOption> getFormOptions(GetOptionRequest request) {
		String method = request.getOptionMethod();
		try {
			// get form option by reflection
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

	@Override
	public List<SelectOption> getStatusTransitions(String projectConfig, String issueKey, String previousStatus) throws Exception {
		TapdProjectConfig config = getProjectConfig(projectConfig);
		List<SelectOption> statusOptions = new ArrayList<>();
		if (StringUtils.isBlank(issueKey)) {
			// 缺陷ID为空时, 获取起始工作流
			SelectOption firstStepWorkFlow = tapdClient.getFirstStepWorkFlow(TapdSystemType.BUG, config.getTapdKey(), null);
			statusOptions.add(firstStepWorkFlow);
		} else {
			statusOptions.addAll(tapdClient.getWorkFlowTransition(TapdSystemType.BUG, config.getTapdKey(), null, previousStatus));
		}
		return statusOptions;
	}

	@Override
	public PluginPager<PlatformDemandDTO> pageDemand(DemandPageRequest request) {
		List<PlatformDemandDTO.Demand> demands = queryDemandList(request, null);
		int total = demands.size();
		if (request.isSelectAll()) {
			// no pager
			// set demand response
			PlatformDemandDTO demandRelatePageData = new PlatformDemandDTO();
			demandRelatePageData.setList(demands);
			return new PluginPager<>(demandRelatePageData, total, Integer.MAX_VALUE, request.getStartPage());
		} else {
			// pager
			demands = demands.stream().skip((long) (request.getStartPage() - 1) * request.getPageSize()).limit(request.getPageSize()).collect(Collectors.toList());
			// set demand response
			PlatformDemandDTO demandRelatePageData = new PlatformDemandDTO();
			demandRelatePageData.setList(demands);
			return new PluginPager<>(demandRelatePageData, total, request.getPageSize(), request.getStartPage());
		}
	}

	@Override
	public PlatformDemandDTO getDemands(DemandRelateQueryRequest request) {
		DemandPageRequest requestParam = new DemandPageRequest();
		requestParam.setProjectConfig(request.getProjectConfig());
		List<PlatformDemandDTO.Demand> demands = queryDemandList(requestParam, request.getRelateDemandIds());
		// set demand response
		PlatformDemandDTO demandRelatePageData = new PlatformDemandDTO();
		demandRelatePageData.setList(demands);
		return demandRelatePageData;
	}

	@Override
	public PlatformBugUpdateDTO addBug(PlatformBugUpdateRequest request) {
		return null;
	}

	@Override
	public PlatformBugUpdateDTO updateBug(PlatformBugUpdateRequest request) {
		return null;
	}

	@Override
	public void deleteBug(String platformBugId) {

	}

	@Override
	public boolean isSupportAttachment() {
		return false;
	}

	@Override
	public void syncAttachmentToPlatform(SyncAttachmentToPlatformRequest request) {

	}

	@Override
	public SyncBugResult syncBugs(SyncBugRequest request) {
		return null;
	}

	@Override
	public void syncAllBugs(SyncAllBugRequest request) {

	}

	@Override
	public void getAttachmentContent(String fileKey, Consumer<InputStream> inputStreamHandler) {

	}

	/**
	 * 查询需求列表
	 *
	 * @param request   需求请求参数
	 * @param filterIds 过滤的需求ID
	 * @return 需求列表
	 */
	private List<PlatformDemandDTO.Demand> queryDemandList(DemandPageRequest request, List<String> filterIds) {
		// validate demand config
		TapdProjectConfig config = getProjectConfig(request.getProjectConfig());
		if (StringUtils.isBlank(config.getTapdKey())) {
			throw new MSPluginException("请在项目中配置Tapd的项目Key!");
		}
		// query demand list no limit
		List<TapdStoryResponse> storys = tapdClient.getProjectStorys(config.getTapdKey(), request.getStartPage(), Integer.MAX_VALUE);
		// handle empty data
		if (CollectionUtils.isEmpty(storys)) {
			return List.of();
		}

		// prepare demand list
		List<PlatformDemandDTO.Demand> demands = new ArrayList<>();
		storys.forEach(story -> {
			PlatformDemandDTO.Demand demand = new PlatformDemandDTO.Demand();
			demand.setDemandId(story.getId());
			demand.setDemandName(story.getName());
			demand.setDemandUrl(tapdClient.getBaseUrl() + "/" + config.getTapdKey() + "/prong/stories/view/" + story.getId());
			boolean isParentDemandShow = StringUtils.isBlank(request.getQuery()) || StringUtils.containsIgnoreCase(demand.getDemandName(), request.getQuery()) || StringUtils.containsIgnoreCase(demand.getDemandId(), request.getQuery()) &&
					(CollectionUtils.isEmpty(request.getExcludeIds()) || !request.getExcludeIds().contains(demand.getDemandId()));
			if (!CollectionUtils.isEmpty(story.getChildren())) {
				List<PlatformDemandDTO.Demand> childrenDemands = new ArrayList<>();
				// handle children demand list
				story.getChildren().forEach(childStory -> {
					PlatformDemandDTO.Demand childDemand = new PlatformDemandDTO.Demand();
					childDemand.setDemandId(childStory.getId());
					childDemand.setDemandName(childStory.getName());
					childDemand.setDemandUrl(tapdClient.getBaseUrl() + "/" + config.getTapdKey() + "/prong/stories/view/" + childStory.getId());
					childDemand.setParent(demand.getDemandId());
					boolean isChildDemandShow = StringUtils.isBlank(request.getQuery()) || StringUtils.containsIgnoreCase(childDemand.getDemandName(), request.getQuery()) || StringUtils.equalsIgnoreCase(childDemand.getDemandId(), request.getQuery()) &&
							(CollectionUtils.isEmpty(request.getExcludeIds()) || !request.getExcludeIds().contains(demand.getDemandId()));
					if (isChildDemandShow) {
						// 满足过滤条件的子需求, 才展示
						childrenDemands.add(childDemand);
					}
				});
				demand.setChildren(childrenDemands);
			}
			if (isParentDemandShow || !CollectionUtils.isEmpty(demand.getChildren())) {
				// 满足过滤条件的父需求, 或者有满足过滤条件的子需求, 才展示
				demands.add(demand);
			}
		});
		// sort by demand id
		demands.sort(Comparator.comparing(PlatformDemandDTO.Demand::getDemandId));
		// filter by condition
		List<PlatformDemandDTO.Demand> filterDemands = demands;
		// filter by ids
		if (!CollectionUtils.isEmpty(filterIds)) {
			filterDemands = filterDemands.stream().filter(demand -> filterIds.contains(demand.getDemandId())).collect(Collectors.toList());
		}
		if (!CollectionUtils.isEmpty(request.getExcludeIds()) && request.isSelectAll()) {
			filterDemands = filterDemands.stream().filter(demand -> !request.getExcludeIds().contains(demand.getDemandId())).collect(Collectors.toList());
		}
		return filterDemands;
	}

	/**
	 * 表单反射调用
	 *
	 * @param request 表单项请求参数
	 * @return 用户下拉选项
	 */
	public List<SelectOption> getOwnerList(GetOptionRequest request) {
		TapdProjectConfig config = getProjectConfig(request.getProjectConfig());
		return tapdClient.getProjectUsers(config.getTapdKey());
	}

	/**
	 * 设置用户平台配置(集成信息)
	 *
	 * @param userPlatformConfig 用户平台配置
	 */
	public void setUserConfig(String userPlatformConfig, Boolean isUserConfig) {
		TapdIntegrationConfig integrationConfig;
		if (isUserConfig) {
			// 如果是用户配置, 则直接从平台参数中获取集成信息, 并替换用户账号配置
			integrationConfig = getIntegrationConfig(TapdIntegrationConfig.class);
			TapdUserPlatformInfo userConfig = PluginUtils.parseObject(userPlatformConfig, TapdUserPlatformInfo.class);
			if (userConfig != null) {
				integrationConfig.setAccount(userConfig.getTapdAccount());
				integrationConfig.setPassword(userConfig.getTapdPassword());
			}
		} else {
			// 如果是集成配置, 则直接从参数中获取集成信息
			integrationConfig = getIntegrationConfig(userPlatformConfig, TapdIntegrationConfig.class);
		}
		validateAndSetConfig(integrationConfig);
	}

	/**
	 * 校验并设置集成配置
	 *
	 * @param config 集成配置
	 */
	private void validateAndSetConfig(TapdIntegrationConfig config) {
		tapdClient.initConfig(config);
	}

	/**
	 * 获取项目配置
	 *
	 * @param configStr 项目配置JSON
	 * @return 项目配置对象
	 */
	private TapdProjectConfig getProjectConfig(String configStr) {
		if (StringUtils.isBlank(configStr)) {
			throw new MSPluginException("请在项目中添加项目配置！");
		}
		return PluginUtils.parseObject(configStr, TapdProjectConfig.class);
	}
}
