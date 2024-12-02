package io.metersphere.platform.client;

import io.metersphere.platform.api.BaseClient;
import io.metersphere.platform.domain.*;
import io.metersphere.platform.domain.response.json.ZentaoBugResponse;
import io.metersphere.plugin.exception.MSPluginException;
import io.metersphere.plugin.utils.JSON;
import io.metersphere.plugin.utils.LogUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RequestCallback;

import java.io.File;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.function.Consumer;

/**
 *
 *  由于禅道最新Latest版本18.10所支持的RESTFUL-API接口不完善, 且不支持附件相关功能;
 *   故保留了JSON-API调用方式供部分不支持接口的调用, 例如附件下载, 附件上传等等;
 *  注意: 禅道JSON-API接口支持配置两种请求方式{PATH_INFO, GET}, 具体请求方式按照配置文件及插件集成配置而定;
 *
 */
public abstract class BaseZentaoJsonClient extends BaseClient {

    protected static String ENDPOINT;

    protected static String USER_NAME;

    protected static String PASSWD;

    public ZentaoJsonApiUrl requestUrl;

    public static final String END_SUFFIX = "/";

    protected static final String FAIL = "fail";

    public BaseZentaoJsonClient(String url) {
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

    /**
     * 登录认证
     *
     * @return sessionId
     */
    public String auth() {
        GetUserResponse getUserResponse = new GetUserResponse();
        String sessionId = "";
        try {
            sessionId = getSessionId();
            String loginUrl = requestUrl.getLogin();
            MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
            paramMap.add("account", USER_NAME);
            paramMap.add("password", PASSWD);
            ResponseEntity<String> response = restTemplate.exchange(loginUrl + sessionId, HttpMethod.POST, getHttpEntity(paramMap), String.class);
            getUserResponse = (GetUserResponse) getResultForObject(GetUserResponse.class, response);
        } catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException("禅道用户获取session"+e.getMessage());
        }
        GetUserResponse.User user = getUserResponse.getUser();
        if (user == null) {
            LogUtil.error(JSON.toJSONString(getUserResponse));
            // 登录失败，获取的session无效，置空session
            MSPluginException.throwException("zentao login fail, user null");
        }
        if (!StringUtils.equals(user.getAccount(), USER_NAME)) {
            LogUtil.error("login fail，inconsistent users");
            MSPluginException.throwException("zentao login fail, inconsistent user");
        }
        return sessionId;
    }

    /**
     * 获取缺陷详情
     *
     * @param id 缺陷ID
     * @return 缺陷详情
     */
    public Map<String, Object> getBugById(String id) {
        String sessionId = auth();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getBugGet(), HttpMethod.GET, getHttpEntity(), String.class, id, sessionId);
        ZentaoBugResponse bugResponse = (ZentaoBugResponse)getResultForObject(ZentaoBugResponse.class, response);
        if (StringUtils.equalsIgnoreCase(bugResponse.getStatus(), FAIL)) {
            ZentaoBugResponse.Bug bug = new ZentaoBugResponse.Bug();
            bug.setId(id);
            bug.setSteps(StringUtils.SPACE);
            bug.setTitle(StringUtils.SPACE);
            bug.setStatus("closed");
            bug.setDeleted("1");
            bug.setOpenedBy(StringUtils.SPACE);
            bugResponse.setData(JSON.toJSONString(bug));
        }
        // noinspection unchecked
        return JSON.parseMap(bugResponse.getData());
    }


    /**
     * 上传附件
     *
     * @param objectType 对象类型
     * @param objectId   对象ID
     * @param file       文件
     */
    public void uploadAttachment(String objectType, String objectId, File file) {
        String sessionId = auth();
        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        FileSystemResource fileResource = new FileSystemResource(file);
        paramMap.add("files", fileResource);
        HttpHeaders header = getHeader();
        header.setContentType(MediaType.parseMediaType("multipart/form-data; charset=UTF-8"));
       HttpEntity<MultiValueMap<String, Object>> httpEntity = getHttpEntity(paramMap, header);

        try {
           ResponseEntity<String> response= restTemplate.exchange(requestUrl.getFileUpload(), HttpMethod.POST, httpEntity,String.class, objectType, objectId,sessionId);
        } catch (Exception e) {
            LogUtil.info("upload zentao attachment error");
        }
    }

    /**
     * 删除附件
     *
     * @param fileId 文件ID
     */
    public void deleteAttachment(String fileId) {
        String sessionId = auth();
        try {
            restTemplate.exchange(requestUrl.getFileDelete(), HttpMethod.GET, getHttpEntity(), String.class, fileId, sessionId);
        } catch (Exception e) {
            LogUtil.info("delete zentao attachment error");
        }
    }

    /**
     * 获取附件字节流
     *
     * @param fileId             文件ID
     * @param inputStreamHandler 流处理
     */
    public void getAttachmentBytes(String fileId, Consumer<InputStream> inputStreamHandler) {
        RequestCallback requestCallback = request -> {
            // 定义请求头的接收类型
            request.getHeaders().setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
            request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip,x-gzip,deflate");
        };

        String sessionId = auth();
        restTemplate.execute(requestUrl.getFileDownload(), HttpMethod.GET,
                requestCallback, (clientHttpResponse) -> {
                    inputStreamHandler.accept(clientHttpResponse.getBody());
                    return null;
                }, fileId, sessionId);
    }

    /**
     * 获取项目下缺陷集合
     *
     * @param pageNum   页码
     * @param pageSize  页面大小
     * @param projectId 项目ID
     * @return 缺陷集合
     */
    public Map<String, Object> getBugsByProductId(Integer pageNum, Integer pageSize, String productId, ZentaoRestClient zentaoRestClient) {
//        String sessionId = auth();
//        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getBugList(),
//                HttpMethod.GET, getHttpEntity(), String.class, projectId, 9999999, pageSize, pageNum, sessionId);

        String url=requestUrl.getBugList();
        ResponseEntity<String> response = restTemplate.exchange(url,
                HttpMethod.GET,zentaoRestClient.getJsonHttpEntityWithToken(StringUtils.EMPTY), String.class, productId, 99999, pageSize, pageNum);
        try {
            // noinspection unchecked
            return JSON.parseMap(JSON.parseMap(response.getBody()).get("data").toString());
        } catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException("请检查配置信息是否填写正确！");
        }
        return null;
    }

    /**
     * 获取sessionId
     *
     * @return sessionId
     */
    private String getSessionId() {
        String getSessionUrl = requestUrl.getSessionGet();
        ResponseEntity<String> response = restTemplate.exchange(getSessionUrl,
                HttpMethod.GET, getHttpEntity(), String.class);
        GetSessionResponse getSessionResponse = (GetSessionResponse) getResultForObject(GetSessionResponse.class, response);
        return JSON.parseObject(getSessionResponse.getData(), GetSessionResponse.Session.class).getSessionID();
    }

    /**
     * 获取请求地址
     *
     * @return 请求地址
     */
    public String getBaseUrl() {
        if (ENDPOINT.endsWith(END_SUFFIX)) {
            return ENDPOINT.substring(0, ENDPOINT.length() - 1);
        }
        return ENDPOINT;
    }

    /**
     * 获取替换图片地址
     *
     * @param replaceImgUrl 替换图片地址
     * @return 替换图片地址
     */
    public String getReplaceImgUrl(String replaceImgUrl) {
        String baseUrl = getBaseUrl();
        String[] split = baseUrl.split("/");
        String suffix = split[split.length - 1];
        if (StringUtils.equals("biz", suffix)) {
            suffix = baseUrl;
        } else if (!StringUtils.containsAny(suffix, "zentao", "pro", "zentaopms", "zentaopro", "zentaobiz")) {
            suffix = "";
        } else {
            suffix = "/" + suffix;
        }
        return String.format(replaceImgUrl, suffix);
    }

    /**
     * 上传文件至禅道缺陷
     *
     * @param file 文件
     * @return 文件ID
     */
    public String uploadFile(File file, String objectId) {
        String id = "";
        String sessionId = auth();
        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        paramMap.add("files", new FileSystemResource(file));
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(requestUrl.getFileUpload(), HttpMethod.POST, getHttpEntity(paramMap),
                    String.class, objectId, sessionId);
            String body = responseEntity.getBody();
            Map obj = JSON.parseMap(body);
            Map data = (Map) JSON.parseObject(obj.get("data").toString());
            Set<String> set = data.keySet();
            if (!set.isEmpty()) {
                id = (String) set.toArray()[0];
            }
        } catch (Exception e) {
            LogUtil.error(e, e.getMessage());
        }
        LogUtil.info("upload file id: " + id);
        return id;
    }

    /**
     * 单独上传图片并获取返回的文件ID
     * @param file 图片文件
     * @return 文件URL
     */
    public String uploadImgFile(File file) {
        String imgUrl = "";
        String sessionId = auth();
        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        paramMap.add("imgFile", new FileSystemResource(file));
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(getBaseUrl() + "/file-ajaxUpload.json?zentaosid={1}", HttpMethod.POST, getHttpEntity(paramMap),
                    String.class, sessionId);
            // noinspection unchecked
            Map<String, Object> dataMap = (Map<String, Object>) JSON.parseMap(responseEntity.getBody());
            imgUrl = dataMap.get("url").toString();
            if (StringUtils.isEmpty(imgUrl)) {
                LogUtil.error("upload img file error");
            }
        } catch (Exception e) {
            LogUtil.error(e, e.getMessage());
        }
        LogUtil.info("upload zentao img url: " + imgUrl);
        return imgUrl;
    }

    protected HttpHeaders getHeader() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set(HttpHeaders.ACCEPT_ENCODING, "gzip,x-gzip,deflate");
        return httpHeaders;
    }

    protected HttpEntity<MultiValueMap<String, Object>> getHttpEntity() {
        return new HttpEntity<>(getHeader());
    }

    protected HttpEntity<MultiValueMap<String, Object>> getHttpEntity(MultiValueMap<String, Object> paramMap) {
        return new HttpEntity<>(paramMap, getHeader());
    }

    protected HttpEntity<MultiValueMap<String, Object>> getHttpEntity(MultiValueMap<String, Object> paramMap, MultiValueMap<String, String> headers) {
        return new HttpEntity<>(paramMap, headers);
    }
    public Map<String, Object> getBuildsV17(String projectId) {
        String sessionId = auth();
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getBuildsGetV17(),
                HttpMethod.GET, getHttpEntity(), String.class, projectId, sessionId);
        return (Map<String, Object>) JSON.parseMap((String) JSON.parseMap(response.getBody()).get("data"));
    }
    public GetCreateMetaDataResponse.MetaData getCreateMetaData(String productID,ZentaoRestClient zentaoRestClient) {
        ResponseEntity<String> response = restTemplate.exchange(requestUrl.getCreateMetaData(),
                HttpMethod.GET,  zentaoRestClient.getJsonHttpEntityWithToken(StringUtils.EMPTY), String.class, productID);
        GetCreateMetaDataResponse getCreateMetaDataResponse = (GetCreateMetaDataResponse) getResultForObject(GetCreateMetaDataResponse.class, response);
        GetCreateMetaDataResponse.MetaData metaData= JSON.parseObject(getCreateMetaDataResponse.getData(), GetCreateMetaDataResponse.MetaData.class);
        return metaData;
    }
    public Map getCustomFields(String productID,ZentaoRestClient zentaoRestClient) {
        //return getCreateMetaData(productID).getCustomFields();
        return getCreateMetaData(productID,zentaoRestClient).getBug();
    }

    public LinkedList<Map<String, Object>> getBuildsByCreateMetaData(String projectId,ZentaoRestClient zentaoRestClient) {
        return getCreateMetaData(projectId,zentaoRestClient).getBuilds();

    }
}

