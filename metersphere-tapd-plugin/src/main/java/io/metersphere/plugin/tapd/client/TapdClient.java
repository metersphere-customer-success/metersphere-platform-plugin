package io.metersphere.plugin.tapd.client;

import io.metersphere.plugin.platform.spi.BaseClient;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.tapd.constants.TapdUrl;
import io.metersphere.plugin.tapd.domain.TapdIntegrationConfig;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;

public class TapdClient extends BaseClient {

	protected static String ENDPOINT = "https://api.tapd.cn";

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

	protected HttpEntity<MultiValueMap<String, String>> getAuthHttpEntity() {
		return new HttpEntity<>(getAuthHeader());
	}

	protected HttpHeaders getAuthHeader() {
		return getBasicHttpHeaders(USERNAME, PASSWORD);
	}
}
