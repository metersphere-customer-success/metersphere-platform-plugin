package io.metersphere.platform.client;

import io.metersphere.platform.domain.Jira9XField;
import io.metersphere.platform.domain.JiraCreateMetadataResponse;
import io.metersphere.plugin.exception.MSPluginException;
import io.metersphere.plugin.utils.JSON;
import io.metersphere.plugin.utils.LogUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

public class JiraClient9X  extends JiraClientV2{
    public Map<String, JiraCreateMetadataResponse.Field> getCreateMetadata(String projectKey, String issueType) {
        String url = getBaseUrl() + "/issue/createmeta/{1}/issuetypes/{2}";
        ResponseEntity<String> response = null;
        try {
            response = this.restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class, new Object[] { projectKey, issueType });
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSPluginException.throwException(e.getMessage());
        }
        Map<String, JiraCreateMetadataResponse.Field> fields = null;
        try {
            Map<String, Object> map = JSON.parseMap((String)response.getBody());
            Object values = map.get("values");
            fields = new HashMap<>();
            List<Jira9XField> list = JSON.parseArray(JSON.toJSONString(values), Jira9XField.class);
            for (Jira9XField item : list)
                fields.put(item.getFieldId(), item);
        } catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException("请检查服务集成信息或项目配置信息");
        }
        return fields;
    }
}
