package io.metersphere.plugin.tapd.impl;

import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.dto.SyncBugResult;
import io.metersphere.plugin.platform.dto.request.*;
import io.metersphere.plugin.platform.dto.response.PlatformBugUpdateDTO;
import io.metersphere.plugin.platform.dto.response.PlatformCustomFieldItemDTO;
import io.metersphere.plugin.platform.dto.response.PlatformDemandDTO;
import io.metersphere.plugin.platform.spi.AbstractPlatform;
import io.metersphere.plugin.platform.utils.PluginPager;
import io.metersphere.plugin.tapd.client.TapdClient;
import io.metersphere.plugin.tapd.domain.TapdIntegrationConfig;
import org.pf4j.Extension;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

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

	}

	@Override
	public void validateProjectConfig(String projectConfig) {

	}

	@Override
	public boolean isSupportDefaultTemplate() {
		return false;
	}

	@Override
	public List<PlatformCustomFieldItemDTO> getDefaultTemplateCustomField(String projectConfig) {
		return null;
	}

	@Override
	public List<SelectOption> getFormOptions(GetOptionRequest optionsRequest) {
		return null;
	}

	@Override
	public List<SelectOption> getStatusTransitions(String projectConfig, String issueKey) throws Exception {
		return null;
	}

	@Override
	public PluginPager<PlatformDemandDTO> pageDemand(DemandPageRequest request) {
		return null;
	}

	@Override
	public PlatformDemandDTO getDemands(DemandRelateQueryRequest request) {
		return null;
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
}
