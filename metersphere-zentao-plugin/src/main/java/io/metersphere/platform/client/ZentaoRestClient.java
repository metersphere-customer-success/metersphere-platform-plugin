package io.metersphere.platform.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.metersphere.platform.constants.ZentaoRestApiUrl;
import io.metersphere.platform.domain.response.rest.*;
import io.metersphere.plugin.exception.MSPluginException;
import io.metersphere.plugin.utils.JSON;
import io.metersphere.plugin.utils.LogUtil;
import io.metersphere.platform.api.BaseClient;
import io.metersphere.platform.domain.*;
import io.metersphere.platform.utils.UnicodeConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;



public class ZentaoRestClient extends BaseClient {

    protected String ENDPOINT;

    protected String USER_NAME;

    protected String PASSWD;


    public String type="projects";
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * Restful API版本
     */
    protected static final String API_VERSION = "v1";
    private static final String ERROR_RESPONSE_KEY = "error";
    private static final String SUCCESS_RESPONSE_KEY = "success";

    public ZentaoRestClient(String url) {
        ENDPOINT = url;
    }
    /**
     * 初始化禅道配置
     *
     * @param config 集成配置
     */
    public void initConfig(ZentaoConfig config) {
        if (config == null) {
            MSPluginException.throwException("禅道服务集成配置为空");
        }
        USER_NAME = config.getAccount();
        PASSWD = config.getPassword();
        ENDPOINT = config.getUrl();
    }

    private String getRestUrl(String url, String type) {
       // return getBaseUrl() + "api.php/" + API_VERSION + (StringUtils.isEmpty(type) ? StringUtils.EMPTY : "/" + type) + url;
        //这里指定使用项目
        return getBaseUrl() + "/api.php/" + API_VERSION + (StringUtils.isEmpty(type) ? StringUtils.EMPTY : "/" +type) + url;
    }
    protected HttpEntity<MultiValueMap> getHttpEntity() {
        return new HttpEntity<>(getHeader());
    }



    protected HttpHeaders getHeader() {
        HttpHeaders httpHeaders = new HttpHeaders();
       // httpHeaders.set(HttpHeaders.ACCEPT_ENCODING, "gzip,x-gzip,deflate");
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        return httpHeaders;
    }


    /**
     * 登录认证
     */
    public void auth() {
        if (StringUtils.isBlank(getToken())) {
            MSPluginException.throwException("禅道认证失败!");
        }
    }
    /**
     * 获取请求参数(token)
     *
     * @param json Body请求参数
     * @return 请求参数
     */
    protected HttpEntity<String> getJsonHttpEntityWithToken(String json) {
        HttpHeaders header = getHeader();
        header.add("Token", getToken());
        return new HttpEntity<>(json, header);
    }
    /**
     * 获取请求参数(no-token)
     *
     * @param jsonObj json对象
     * @return 请求参数
     */
    protected HttpEntity<String> getJsonHttpEntity(ObjectNode jsonObj) {
        return new HttpEntity<>(jsonObj.toString(), getHeader());
    }
    /**
     * 获取Token
     *
     * @return token
     */
    public String getToken() {
        ObjectNode jsonObj = objectMapper.createObjectNode();
        jsonObj.put("account", USER_NAME);
        jsonObj.put("password", PASSWD);
        ResponseEntity<ZentaoRestTokenResponse> response = null;
        try {
            response = restTemplate.postForEntity(getRestUrl(ZentaoRestApiUrl.GET_TOKEN, null), getJsonHttpEntity(jsonObj), ZentaoRestTokenResponse.class);
            if (response.getBody() == null) {
              MSPluginException.throwException("禅道认证失败: 地址错误或未获取到Token");
            }
        } catch (Exception e) {
            if (e instanceof HttpClientErrorException && ((HttpClientErrorException.BadRequest) e).getStatusCode().is4xxClientError()) {
                MSPluginException.throwException("禅道认证失败: 账号或密码错误");
            } else {
                MSPluginException.throwException("禅道认证失败: 地址错误或连接超时");
            }
        }
        return response.getBody().getToken();
    }

    public AddIssueResponse.Issue addIssue(Map<String, Object> paramMap) {

        String customProject=paramMap.get("product").toString();
        ResponseEntity<String> response = null;
        try {

            String url=getRestUrl(ZentaoRestApiUrl.ADD_BUG, null);

            response = restTemplate.exchange(url, HttpMethod.POST, getJsonHttpEntityWithToken(JSON.toJSONString(paramMap)), String.class,customProject);

        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSPluginException.throwException("创建bug 异常"+e.getMessage());
        }

        AddIssueResponse.Issue issue = null;
        try {
            issue = JSON.parseObject(response.getBody(), AddIssueResponse.Issue.class);
        } catch (Exception e) {
            LogUtil.error(e);
        }
        if (issue == null) {
            MSPluginException.throwException(UnicodeConvertUtils.unicodeToCn(response.getBody()));
        }
        return issue;
    }

    public String getCustomProject(Map<String, Object> param) {
        if (param.containsKey("project") && !param.get("project").toString().isEmpty()) {
            return param.get("project").toString();
        }

        return StringUtils.EMPTY;
    }
    public void updateIssue(String id, Map<String, Object> paramMap) {

        try {
            String url=getRestUrl(ZentaoRestApiUrl.GET_OR_UPDATE_OR_DELETE_BUG, null);
            String mapStr=JSON.toJSONString(paramMap);
            ResponseEntity<String>   response = restTemplate.exchange(url,
                    HttpMethod.PUT, getJsonHttpEntityWithToken(mapStr),String.class, id);
            AddIssueResponse.Issue issue = (AddIssueResponse.Issue) getResultForObject(AddIssueResponse.Issue.class, response);
            if (response.getBody()==null) {
                // 如果没改啥东西保存也会报错，addIssueResponse.getData() 值为 "[]"
                //MSPluginException.throwException(UnicodeConvertUtils.unicodeToCn(issue.toString()));
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSPluginException.throwException("更新缺陷失败："+e.getMessage());
        }
    }

    public void deleteIssue(String id) {
       // String sessionId = login();
        ResponseEntity<String> response;
        try {

            response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.GET_OR_UPDATE_OR_DELETE_BUG, null), HttpMethod.DELETE, getJsonHttpEntityWithToken(StringUtils.EMPTY), String.class, id);
            if (response.getBody() == null || !response.getBody().contains(SUCCESS_RESPONSE_KEY)) {
                throw new Exception("删除禅道缺陷失败!");
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSPluginException.throwException(e.getMessage());
        }
    }

    public ZentaoRestBugDetailResponse get(String id) {

        ResponseEntity<ZentaoRestBugDetailResponse> response = null;
        try {
            response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.GET_OR_UPDATE_OR_DELETE_BUG, null), HttpMethod.GET, getJsonHttpEntityWithToken(StringUtils.EMPTY), ZentaoRestBugDetailResponse.class, id);
            if (response.getBody() == null) {
                throw new Exception("获取禅道缺陷详情失败!");
            }
        } catch (Exception e) {
            MSPluginException.throwException("查询bug失败："+e.getMessage());
        }
        return response.getBody();
    }


    /**
     * @Description 获取禅道版本。列表
     * @param projectId
     * @return Map<String, Object>
     */
    public ZentaoRestBuildResponse getBuilds(String projectId) {

        ResponseEntity<ZentaoRestBuildResponse> response = null;
        try {
            response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.GET_BUILDS, null), HttpMethod.GET, getJsonHttpEntityWithToken(StringUtils.EMPTY), ZentaoRestBuildResponse.class, projectId);
            if (response.getBody() == null) {
                throw new Exception("获取禅道版本列表失败!");
            }
        } catch (Exception e) {
            MSPluginException.throwException("获取禅道版本列表失败："+e.getMessage());
        }
        return response.getBody();
    }

//    public Map<String, Object> getUsers() {
//        String sessionId = login();
//        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getUserGet() + sessionId,
//                HttpMethod.GET, getHttpEntity(), String.class);
//        return (Map<String, Object>) JSON.parseMap(response.getBody());
//    }
    /**
     * 获取禅道用户列表
     *
     * @return 用户列表
     */
    public ZentaoRestUserResponse getUsers(int page, int limit) {
        ResponseEntity<ZentaoRestUserResponse> response = null;
        try {
            response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.GET_USERS, null), HttpMethod.GET, getJsonHttpEntityWithToken(StringUtils.EMPTY), ZentaoRestUserResponse.class, page, limit);
            if (response.getBody() == null) {
                throw new Exception("获取禅道用户列表失败!");
            }
        } catch (Exception e) {
            MSPluginException.throwException(UnicodeConvertUtils.unicodeToCn(e.getMessage()));
        }
        return response.getBody();
    }

    public ZentaoRestDemandResponse getDemands(String projectKey,String type) {

        ResponseEntity<ZentaoRestDemandResponse> response = null;
        try {
            response = restTemplate.exchange(getRestUrl(ZentaoRestApiUrl.LIST_DEMAND, type), HttpMethod.GET, getJsonHttpEntityWithToken(StringUtils.EMPTY), ZentaoRestDemandResponse.class, projectKey,1,999999);
            if (response.getBody() == null) {
                throw new Exception("获取禅道项目需求列表!");
            }
        } catch (Exception e) {
            MSPluginException.throwException (UnicodeConvertUtils.unicodeToCn(e.getMessage()));
        }
        return response.getBody();
    }

//


    public String getBaseUrl() {
        if (ENDPOINT.endsWith("/")) {
            return ENDPOINT.substring(0, ENDPOINT.length() - 1);
        }
        return ENDPOINT;
    }




    public String getReplaceImgUrl(String replaceImgUrl) {
        String baseUrl = getBaseUrl();
        String[] split = baseUrl.split("/");
        String suffix = split[split.length - 1];
        if (StringUtils.equals("biz", suffix)) {
            suffix = baseUrl;
        } else if (!StringUtils.equalsAny(suffix, "zentao", "pro", "zentaopms", "zentaopro", "zentaobiz")) {
            suffix = "";
        } else {
            suffix = "/" + suffix;
        }
        return String.format(replaceImgUrl, suffix);
    }

    public void checkProjectExist(String relateId,String type) {
        //String sessionId = login();
         String url=getRestUrl(ZentaoRestApiUrl.GET_PRODUCT_OR_PROJECT,type);
        //String url=requestUrl.getProductGet();
//        ResponseEntity<String> response = restTemplate.exchange(url,
//                HttpMethod.GET, getHttpEntity(), String.class, relateId, sessionId);
        ResponseEntity<Map> response;
        response = restTemplate.exchange(url, HttpMethod.GET, getJsonHttpEntityWithToken(StringUtils.EMPTY), Map.class, relateId);
        try {

            if (response.getBody() == null || response.getBody().containsKey("error")) {
                throw new Exception("产品或项目不存在!");
            }
        } catch (Exception e) {
            if (HttpStatus.BAD_REQUEST==(((HttpClientErrorException) e).getStatusCode())) {
                MSPluginException.throwException("验证产品或项目参数有误!");
            }
            if (HttpStatus.FORBIDDEN==(((HttpClientErrorException) e).getStatusCode())) {
                MSPluginException.throwException("未通过认证, 无法验证产品或项目!");
            }
            if (HttpStatus.NOT_FOUND==(((HttpClientErrorException) e).getStatusCode())) {
                MSPluginException.throwException("产品或项目不存在!");
            }
            if (((HttpClientErrorException) e).getStatusCode().is5xxServerError()) {
                 MSPluginException.throwException("验证失败, 服务器异常!");
            }
            LogUtil.error("checkProjectExist error: " + response.getBody());
            MSPluginException.throwException("验证失败");
        }

    }



    public ResponseEntity proxyForGet(String path, Class responseEntityClazz) {
        LogUtil.info("zentao proxyForGet: Path=" + path);
        String url = this.ENDPOINT + path;
        validateProxyUrl(url, "/index.php", "/file-read-");
        ResponseEntity response=restTemplate.exchange(url, HttpMethod.GET, getJsonHttpEntityWithToken(StringUtils.EMPTY), responseEntityClazz);
        //LogUtil.info("获取图片响应:"+response);
        return response;
    }
}
