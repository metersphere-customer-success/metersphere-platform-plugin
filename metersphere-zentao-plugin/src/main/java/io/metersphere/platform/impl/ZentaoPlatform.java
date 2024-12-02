package io.metersphere.platform.impl;

import io.metersphere.platform.client.BaseZentaoJsonClient;
import io.metersphere.platform.domain.response.rest.*;
import io.metersphere.plugin.exception.MSPluginException;
import io.metersphere.plugin.utils.JSON;
import io.metersphere.plugin.utils.LogUtil;
import io.metersphere.base.domain.IssuesWithBLOBs;
import io.metersphere.platform.api.AbstractPlatform;
import io.metersphere.platform.client.ZentaoRestClient;
import io.metersphere.platform.client.ZentaoFactory;
import io.metersphere.platform.client.ZentaoGetClient;
import io.metersphere.platform.constants.AttachmentSyncType;
import io.metersphere.platform.domain.*;
import io.metersphere.platform.utils.DateUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ZentaoPlatform extends AbstractPlatform {
    protected BaseZentaoJsonClient zentaoJsonClient;

    protected final ZentaoRestClient zentaoRestClient;

    protected final String[] imgArray = {
            "bmp", "jpg", "png", "tif", "gif", "jpeg"
    };

    protected Map<String, String> buildMap;

    public ZentaoPlatform(PlatformRequest request) {
        super.key = ZentaoPlatformMetaInfo.KEY;
        super.request = request;
        ZentaoConfig zentaoConfig = getIntegrationConfig(ZentaoConfig.class);
        //this.zentaoRestClient = ZentaoFactory.getInstance(zentaoConfig.getUrl(), zentaoConfig.getRequest());
        this.zentaoJsonClient = ZentaoFactory.getInstance(zentaoConfig.getUrl(), zentaoConfig.getRequest());
        this.zentaoRestClient =new ZentaoRestClient(zentaoConfig.getUrl());
        zentaoRestClient.initConfig(zentaoConfig);
    }


    public ZentaoProjectConfig getProjectConfig(String configStr) {
        if (StringUtils.isBlank(configStr)) {
            MSPluginException.throwException("请在项目中添加项目配置！");
        }
        ZentaoProjectConfig projectConfig = JSON.parseObject(configStr, ZentaoProjectConfig.class);
        return projectConfig;
    }

    @Override
    public IssuesWithBLOBs addIssue(PlatformIssuesUpdateRequest request) {
        setUserConfig(request.getUserPlatformUserConfig());

       Map<String, Object> param = buildUpdateParam(request);
       LogUtil.info("addIssue请求参数："+JSON.toJSONString(param));
        AddIssueResponse.Issue issue = zentaoRestClient.addIssue(param);
        request.setPlatformStatus(issue.getStatus());

        String id = issue.getId();
        if (StringUtils.isNotBlank(id)) {
            request.setPlatformId(id);
            request.setId(UUID.randomUUID().toString());
        } else {
            MSPluginException.throwException("请确认该Zentao账号是否开启超级model调用接口权限");
        }
        return request;
    }

    @Override
    public IssuesWithBLOBs updateIssue(PlatformIssuesUpdateRequest request) {
        setUserConfig(request.getUserPlatformUserConfig());
        Map<String, Object> param = buildUpdateParam(request);
        if (request.getTransitions() != null) {
            request.setPlatformStatus(request.getTransitions().getValue());
        }
        this.handleZentaoBugStatus(param);
        zentaoRestClient.updateIssue(request.getPlatformId(), param);
        return request;
    }

    /**
     * 更新缺陷数据
     *
     * @param issue 待更新缺陷数据
     * @param bug   平台缺陷数据
     * @return
     */
    public IssuesWithBLOBs getUpdateIssues(PlatformIssuesDTO issue, Map bug) {

        GetIssueResponse.Issue bugObj = JSON.parseObject(JSON.toJSONString(bug), GetIssueResponse.Issue.class);
        String description = bugObj.getSteps();
        String steps = description;
        try {
            steps = htmlDesc2MsDesc(zentao2MsDescription(description));
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
        if (issue == null) {
            issue = new PlatformIssuesDTO();
            if (StringUtils.isNotBlank(defaultCustomFields)) {
                issue.setCustomFieldList(JSON.parseArray(defaultCustomFields, PlatformCustomFieldItemDTO.class));
            } else {
                issue.setCustomFieldList(new ArrayList<>());
            }
        } else {
            mergeCustomField(issue, defaultCustomFields);
        }
        issue.setPlatformStatus(bugObj.getStatus());
        if (StringUtils.equals(bugObj.getDeleted(), "1")) {
            issue.setPlatformStatus("DELETE");
        }
        issue.setTitle(bugObj.getTitle());
        issue.setDescription(steps);
        issue.setReporter(bugObj.getOpenedBy());
        issue.setPlatform(key);
        try {
            String openedDate = bug.get("openedDate").toString();
            String lastEditedDate = bug.get("lastEditedDate").toString();
            if (StringUtils.isNotBlank(openedDate) && !openedDate.startsWith("0000-00-00"))
                issue.setCreateTime(DateUtils.getTime(openedDate).getTime());
            if (StringUtils.isNotBlank(lastEditedDate) && !lastEditedDate.startsWith("0000-00-00"))
                issue.setUpdateTime(DateUtils.getTime(lastEditedDate).getTime());
        } catch (Exception e) {
            LogUtil.error("update zentao time" + e.getMessage());
        }
        if (issue.getUpdateTime() == null) {
            issue.setUpdateTime(System.currentTimeMillis());
        }
        List<PlatformCustomFieldItemDTO> customFieldList = syncIssueCustomFieldList(issue.getCustomFieldList(), bug);
        handleSpecialField(customFieldList);
        issue.setCustomFields(JSON.toJSONString(customFieldList));
        return issue;
    }

    private void handleSpecialField(List<PlatformCustomFieldItemDTO> customFieldList) {
        for (PlatformCustomFieldItemDTO item : customFieldList) {
            if (StringUtils.equals(item.getId(), "openedBuild") && StringUtils.isNotBlank(item.getValue().toString())) {
                String[] split = item.getValue().toString().split(",");
                if (buildMap != null) {
                    for (int i = 0; i < split.length; i++) {
                        String s = split[i];
                        if (StringUtils.isNotBlank(buildMap.get(s))) {
                            split[i] = buildMap.get(s);
                        }
                    }
                }
                ArrayList<String> arrayList = new ArrayList<>(Arrays.asList(split));
                item.setValue(arrayList);
              //  break;
            }
            //处理严重程度和优先级，处理成字符串，适配MS 单选框id
            if (StringUtils.equals(item.getCustomData(), "severity")) {
                item.setValue(item.getValue().toString());
               // LogUtil.info("处理severity"+item.getValue());
            }
            if (StringUtils.equals(item.getCustomData(), "pri")) {
                item.setValue(item.getValue().toString());
                //LogUtil.info("处理pri"+item.getValue());
            }
        }
    }

    @Override
    public List<SelectOption> getFormOptions(GetOptionRequest request)  {
        return getFormOptions(this, request);
    }

    @Override
    public void deleteIssue(String platformId) {
        zentaoRestClient.deleteIssue(platformId);
    }

    @Override
    public void validateIntegrationConfig() {
        zentaoRestClient.auth();
    }

    @Override
    public void validateProjectConfig(String projectConfig) {
        String zentaoId=getProjectConfig(projectConfig).getZentaoId();
        if(zentaoId.contains("-")) {
            zentaoRestClient.checkProjectExist(zentaoId.split("-")[1], "projects");
        }else {
            zentaoRestClient.checkProjectExist(zentaoId, "products");
        }
    }

    public ZentaoConfig setUserConfig(String userPlatformInfo) {
        ZentaoConfig zentaoConfig = getIntegrationConfig(ZentaoConfig.class);
        ZentaoPlatformUserInfo userInfo = getZentaoPlatformUserInfo(userPlatformInfo);
        if (StringUtils.isNotBlank(userInfo.getZentaoUserName())
                && StringUtils.isNotBlank(userInfo.getZentaoPassword())) {
            zentaoConfig.setAccount(userInfo.getZentaoUserName());
            zentaoConfig.setPassword(userInfo.getZentaoPassword());
        }
        zentaoRestClient.initConfig(zentaoConfig);
        zentaoJsonClient.initConfig(zentaoConfig);
        return zentaoConfig;
    }

    private ZentaoPlatformUserInfo getZentaoPlatformUserInfo(String userPlatformInfo) {
        return StringUtils.isBlank(userPlatformInfo) ? new ZentaoPlatformUserInfo()
                : JSON.parseObject(userPlatformInfo, ZentaoPlatformUserInfo.class);
    }

    @Override
    public void validateUserConfig(String userConfig) {
        ZentaoPlatformUserInfo userInfo = getZentaoPlatformUserInfo(userConfig);
        if (StringUtils.isBlank(userInfo.getZentaoUserName())
                || StringUtils.isBlank(userInfo.getZentaoPassword())) {
            MSPluginException.throwException("请填写账号信息");
        }
        setUserConfig(userConfig);
        zentaoRestClient.auth();
    }

    @Override
    public boolean isAttachmentUploadSupport() {
        return true;
    }

//    public IssuesDao getZentaoAssignedAndBuilds(IssuesDao issue) {
//        Map zentaoIssue = (Map) zentaoClient.getBugById(issue.getPlatformId());
//        String assignedTo = zentaoIssue.get("assignedTo").toString();
//        String openedBuild = zentaoIssue.get("openedBuild").toString();
//        List<String> zentaoBuilds = new ArrayList<>();
//        if (Strings.isNotBlank(openedBuild)) {
//            zentaoBuilds = Arrays.asList(openedBuild.split(","));
//        }
//        issue.setZentaoAssigned(assignedTo);
//        issue.setZentaoBuilds(zentaoBuilds);
//        return issue;
//    }

    /**
     * 反射调用，勿删
     * @param request
     * @return
     */
    public List<SelectOption> getBuilds(GetOptionRequest request) {
        ZentaoProjectConfig projectConfig = getProjectConfig(request.getProjectConfig());
        ZentaoRestBuildResponse builds = new ZentaoRestBuildResponse();
        try {
            if(projectConfig.getZentaoId().contains("-")) {
                builds = zentaoRestClient.getBuilds(projectConfig.getZentaoId().split("-")[1]);
            }
            
        } catch (Exception e) {
           // builds = zentaoClient.getBuildsV17(projectConfig.getZentaoId());
        }

//        List<SelectOption> res = new ArrayList<>();
//        if (builds != null) {
//            builds.forEach((k, v) -> {
//                if (StringUtils.isNotBlank(k) && v != null) {
//                    res.add(new SelectOption(v.toString(), k));
//                }
//            });
//        }
        List<SelectOption> buildOptions = builds.getBuilds().stream().map(user -> new SelectOption(user.getName(), user.getId())).collect(Collectors.toList());
        buildOptions.add(new SelectOption("主干", "trunk"));
        return buildOptions;
    }

    /**
     * 反射调用，勿删
     * @param request
     * @return
     */
    public List<SelectOption> getUsers(GetOptionRequest request) {

        ZentaoRestUserResponse users = zentaoRestClient.getUsers(1, Integer.MAX_VALUE);
        List<SelectOption> userOptions = users.getUsers().stream().map(user -> new SelectOption(user.getRealname(), user.getAccount())).collect(Collectors.toList());
        return userOptions;
    }

    @Override
    public SyncIssuesResult syncIssues(SyncIssuesRequest request) {
        List<PlatformIssuesDTO> issues = request.getIssues();
        SyncIssuesResult syncIssuesResult = new SyncIssuesResult();
        this.defaultCustomFields = request.getDefaultCustomFields();
        issues.forEach(item -> {
            //Map bug = zentaoRestClient.getBugById(item.getPlatformId());
            Map bug = zentaoJsonClient.getBugById(item.getPlatformId());
            getUpdateIssues(item, bug);
            syncIssuesResult.getUpdateIssues().add(item);
            syncZentaoIssueAttachments(syncIssuesResult, item);
        });
        return syncIssuesResult;
    }

    @Override
    public void syncAllIssues(SyncAllIssuesRequest syncRequest) {
        int pageNum = 1;
        int pageSize = 200;

        int currentSize;

        ZentaoProjectConfig projectConfig = getProjectConfig(syncRequest.getProjectConfig());
        this.defaultCustomFields = syncRequest.getDefaultCustomFields();

        setBuildOptions(syncRequest);
        String productId =projectConfig.getZentaoId();
        String projectId;
        if(projectConfig.getZentaoId().contains("-")) {
             productId=projectConfig.getZentaoId().split("-")[0];
             projectId = projectConfig.getZentaoId().split("-")[1];
        } else {
            projectId = "";
        }
        try {
            do {
                SyncAllIssuesResult syncIssuesResult = new SyncAllIssuesResult();

                // 获取禅道平台缺陷
                Map<String, Object> response = zentaoJsonClient.getBugsByProductId(pageNum, pageSize,productId,zentaoRestClient);
                LinkedHashMap<String,LinkedHashMap>  bugs = (LinkedHashMap<String,LinkedHashMap>) response.get("bugs");
                List<Map>   zentaoIssues=new ArrayList<Map>(bugs.values());
                if(StringUtils.isNotBlank(projectId)){
                    zentaoIssues = zentaoIssues.stream().filter(map -> ( map.get("project").toString().equals(projectId))).collect(Collectors.toList());
                }

                currentSize = zentaoIssues.size();

                List<String> allIds = zentaoIssues.stream().map(i -> i.get("id").toString()).collect(Collectors.toList());

                syncIssuesResult.setAllIds(allIds);

                if (syncRequest != null) {
                    zentaoIssues = filterSyncZentaoIssuesByCreated(zentaoIssues, syncRequest);
                }

                if (CollectionUtils.isNotEmpty(zentaoIssues)) {
                    for (Map zentaoIssue : zentaoIssues) {
                        String platformId = Integer.toString((int)zentaoIssue.get("id"));
                        IssuesWithBLOBs issue = getUpdateIssues(null, zentaoIssue);


                        // 设置临时UUID，同步附件时需要用
                        issue.setId(UUID.randomUUID().toString());

                        issue.setPlatformId(platformId);
                        syncIssuesResult.getUpdateIssues().add(issue);

                        //同步第三方平台系统附件字段
                        syncZentaoIssueAttachments(syncIssuesResult, issue);
                    }
                }

                pageNum++;

                HashMap<Object, Object> syncParam = buildSyncAllParam(syncIssuesResult);
                syncRequest.getHandleSyncFunc().accept(syncParam);

                if (pageNum > (Integer)((Map)response.get("pager")).get("pageTotal")) {
                    // 禅道接口有点恶心，pageNum 超过了总页数，还是会返回最后一页的数据，当缺陷总数是pageSize的时候会死循环
                    break;
                }
            } while (currentSize >= pageSize);
        } catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException(e);
        }
    }

    private void setBuildOptions(SyncAllIssuesRequest syncRequest) {
        try {
            GetOptionRequest request = new GetOptionRequest();
            request.setProjectConfig(syncRequest.getProjectConfig());
            this.buildMap = getBuilds(request).stream().collect(Collectors.toMap(SelectOption::getText, SelectOption::getValue));
        } catch (Exception e) {
            LogUtil.error(e);
        }
    }

    public List<Map> filterSyncZentaoIssuesByCreated(List<Map> zentaoIssues, SyncAllIssuesRequest syncRequest) {
        if (syncRequest.getCreateTime() == null) {
            return zentaoIssues;
        }
        List<Map> filterIssues = zentaoIssues.stream().filter(item -> {
            long createTimeMills = 0;
            try {
                createTimeMills = DateUtils.getTime((String) item.get("openedDate")).getTime();
                if (syncRequest.isPre()) {
                    return createTimeMills <= syncRequest.getCreateTime().longValue();
                } else {
                    return createTimeMills >= syncRequest.getCreateTime().longValue();
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }).collect(Collectors.toList());
        return filterIssues;
    }

    @Override
    public List<PlatformCustomFieldItemDTO> getThirdPartCustomField(String projectConfig) {
        return null;
    }

    @Override
    public void syncIssuesAttachment(SyncIssuesAttachmentRequest request) {
        String syncType = request.getSyncType();
        File file = request.getFile();
        String platformId = request.getPlatformId();
        if (StringUtils.equals(AttachmentSyncType.UPLOAD.syncOperateType(), syncType)) {
            // 上传附件
            zentaoJsonClient.uploadAttachment("bug", platformId, file);
        } else if (StringUtils.equals(AttachmentSyncType.DELETE.syncOperateType(), syncType)) {
            ZentaoRestBugDetailResponse bugInfo = zentaoRestClient.get(platformId);
            Object files  =  bugInfo.getFiles();
            if (files instanceof Map) {
                // noinspection unchecked
                Map<String, LinkedHashMap<String, Object>> zenFiles = (Map<String, LinkedHashMap<String, Object>>) files;
                for (String fileId : zenFiles.keySet()) {
                    LinkedHashMap<String, Object> zenFileMap = zenFiles.get(fileId);
                    if (StringUtils.equals(file.getName(), zenFileMap.get("title").toString())) {
                        zentaoJsonClient.deleteAttachment(fileId);
                        break;
                    }
                }
            }
//            for (String fileId : zenFiles.keySet()) {
//                Map fileInfo = (Map) zenFiles.get(fileId);
//                if (file.getName().equals(fileInfo.get("title"))) {
//                    zentaoJsonClient.deleteAttachment(fileId);
//                    break;
//                }
//            }
        }
    }

    @Override
    public List<PlatformStatusDTO> getStatusList(String projectConfig) {
        List<PlatformStatusDTO> platformStatusDTOS = new ArrayList<>();
        for (ZentaoIssuePlatformStatus status : ZentaoIssuePlatformStatus.values()) {
            PlatformStatusDTO platformStatusDTO = new PlatformStatusDTO();
            platformStatusDTO.setValue(status.name());
            platformStatusDTO.setLabel(status.getName());

            platformStatusDTOS.add(platformStatusDTO);
        }
        return platformStatusDTOS;
    }

    @Override
    public List<DemandDTO> getDemands(String projectConfigStr) {
        List<DemandDTO> list = new ArrayList<>();
        try {
            ZentaoProjectConfig projectConfig = getProjectConfig(projectConfigStr);

          String zentaoId=projectConfig.getZentaoId();
            ZentaoRestDemandResponse obj=new ZentaoRestDemandResponse();
          if(zentaoId.contains("-")){
               obj= zentaoRestClient.getDemands(zentaoId.split("-")[1],"projects");
          }else {
               obj = zentaoRestClient.getDemands(zentaoId,"products");
          }


            if (obj != null) {
                //String data = obj.get("data").toString();
                //if (StringUtils.isBlank(data)) {
                List<ZentaoRestDemandResponse.Story> stories=obj.getStories();
                if(stories.size()==0)
                    return list;

                for (int i = 0; i < stories.size(); i++) {
                        DemandDTO demandDTO = new DemandDTO();
                        demandDTO.setId(stories.get(i).getId());
                        demandDTO.setName(stories.get(i).getTitle());
                        demandDTO.setPlatform(key);
                        list.add(demandDTO);
                }

                // 兼容处理11.5版本格式 [{obj},{obj}]
//                if (data.charAt(0) == '[') {
//                    List array = JSON.parseArray(data);
//                    for (int i = 0; i < array.size(); i++) {
//                        Map o = (Map) array.get(i);
//                        DemandDTO demandDTO = new DemandDTO();
//                        demandDTO.setId(o.get("id").toString());
//                        demandDTO.setName(o.get("title").toString());
//                        demandDTO.setPlatform(key);
//                        list.add(demandDTO);
//                    }
//                }
                // {"5": {"children": {"51": {}}}, "6": {}}
//                else if (data.startsWith("{\"")) {
//                    Map<String, Map<String, String>> dataMap = JSON.parseMap(data);
//                    Collection<Map<String, String>> values = dataMap.values();
//                    values.forEach(v -> {
//                        Map jsonObject = JSON.parseMap(JSON.toJSONString(v));
//                        DemandDTO demandDTO = new DemandDTO();
//                        demandDTO.setId(jsonObject.get("id").toString());
//                        demandDTO.setName(jsonObject.get("title").toString());
//                        demandDTO.setPlatform(key);
//                        list.add(demandDTO);
//                        if (jsonObject.get("children") != null) {
//                            LinkedHashMap<String, Map<String, String>> children = (LinkedHashMap<String, Map<String, String>>) jsonObject.get("children");
//                            Collection<Map<String, String>> childrenMap = children.values();
//                            childrenMap.forEach(ch -> {
//                                DemandDTO dto = new DemandDTO();
//                                dto.setId(ch.get("id"));
//                                dto.setName(ch.get("title"));
//                                dto.setPlatform(key);
//                                list.add(dto);
//                            });
//                        }
//                    });
//                }
                // 处理格式 {{"id": {obj}},{"id",{obj}}}
//                else if (data.charAt(0) == '{') {
//                    Map dataObject = (Map) obj.get("data");
//                    String s = JSON.toJSONString(dataObject);
//                    Map<String, Object> map = JSON.parseMap(s);
//                    Collection<Object> values = map.values();
//                    values.forEach(v -> {
//                        Map jsonObject = JSON.parseMap(JSON.toJSONString(v));
//                        DemandDTO demandDTO = new DemandDTO();
//                        demandDTO.setId(jsonObject.get("id").toString());
//                        demandDTO.setName(jsonObject.get("title").toString());
//                        demandDTO.setPlatform(key);
//                        list.add(demandDTO);
//                    });
//                }
            }
        } catch (Exception e) {
            LogUtil.error("get zentao demand fail: ", e);
        }
        return list;
    }

    private String ms2ZentaoDescription(String msDescription, String projectId) {
        String imgUrlRegex = "!\\[.*?]\\(/resource/md/get(.*?\\..*?)\\)";
        String zentaoSteps = msDescription.replaceAll(imgUrlRegex, zentaoJsonClient.requestUrl.getReplaceImgUrl());
        Matcher matcher = zentaoJsonClient.requestUrl.getImgPattern().matcher(zentaoSteps);
        while (matcher.find()) {
            // get file name
            String originSubUrl = matcher.group(1);
            if (originSubUrl.contains("/url?url=") || originSubUrl.contains("/path?")) {
                String path = URLDecoder.decode(originSubUrl, StandardCharsets.UTF_8);
                String fileName;
                if (path.indexOf("fileID") > 0) {
                    fileName = path.substring(path.indexOf("fileID") + 7);
                } else {
                    fileName = path.substring(path.indexOf("file-read-") + 10);
                }
                zentaoSteps = zentaoSteps.replaceAll(Pattern.quote(originSubUrl), fileName);
            } else {
                String fileName = originSubUrl.substring(10);
                // upload zentao
                try {
                    String id = zentaoJsonClient.uploadFile(getRealMdFile(fileName), projectId);
                    // todo delete local file
                    int index = fileName.lastIndexOf(".");
                    String suffix = "";
                    if (index != -1) {
                        suffix = fileName.substring(index);
                    }
                    // replace id
                    zentaoSteps = zentaoSteps.replaceAll(Pattern.quote(originSubUrl), id + suffix);
                } catch (Exception e) {
                    LogUtil.error(e);
                }
            }
        }
        // image link
        String netImgRegex = "!\\[(.*?)]\\((http.*?)\\)";
        return zentaoSteps.replaceAll(netImgRegex, "<img src=\"$2\" alt=\"$1\"/>");
    }

    public String zentao2MsDescription(String ztDescription) {
        String imgRegex = "<img src.*?/>";
        Pattern pattern = Pattern.compile(imgRegex);
        Matcher matcher = pattern.matcher(ztDescription);
        while (matcher.find()) {
            if (StringUtils.isNotEmpty(matcher.group())) {
                // img标签内容
                String imgPath = matcher.group();
                // 解析标签内容为图片超链接格式，进行替换，
                String src = getMatcherResultForImg("src\\s*=\\s*\"?(.*?)(\"|>|\\s+)", imgPath);
                String alt = getMatcherResultForImg("alt\\s*=\\s*\"?(.*?)(\"|>|\\s+)", imgPath);
                String hyperLinkPath = packageDescriptionByPathAndName(src, alt);
                imgPath = transferSpecialCharacter(imgPath);
                ztDescription = ztDescription.replaceAll(imgPath, hyperLinkPath);
            }
        }

        return ztDescription;
    }

    /**
     * 转译字符串中的特殊字符
     *
     * @param str
     * @return
     */
    protected String transferSpecialCharacter(String str) {
        String regEx = "[`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Pattern pattern = Pattern.compile(regEx);
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
            CharSequence cs = str;
            int j = 0;
            for (int i = 0; i < cs.length(); i++) {
                String temp = String.valueOf(cs.charAt(i));
                Matcher m2 = pattern.matcher(temp);
                if (m2.find()) {
                    StringBuilder sb = new StringBuilder(str);
                    str = sb.insert(j, "\\").toString();
                    j++;
                }
                j++; //转义完成后str的长度增1
            }
        }
        return str;
    }

    private String packageDescriptionByPathAndName(String path, String name) {
        String result = "";

        if (StringUtils.isNotEmpty(path)) {
            if (!path.startsWith("http")) {
                if (path.startsWith("{") && path.endsWith("}")) {
                    String srcContent = path.substring(1, path.length() - 1);
                    if (StringUtils.isEmpty(name)) {
                        name = srcContent;
                    }

                    if (Arrays.stream(imgArray).anyMatch(imgType -> StringUtils.equals(imgType, srcContent.substring(srcContent.indexOf('.') + 1)))) {
                        //if (zentaoRestClient instanceof ZentaoGetClient) {
                        if (zentaoJsonClient instanceof ZentaoGetClient) {
                            path = "/index.php?m=file&f=read&fileID=" + srcContent;
                        } else {
                            // 禅道开源版
                            path = "/file-read-" + srcContent;
                        }
                    } else {
                        return result;
                    }
                } else {
                    name = name.replaceAll("&amp;", "&");
                    path = path.replaceAll("&amp;", "&");
                    if (path.contains("/")) {
                        String[] split = path.split("/");
                        path = "/" + split[split.length - 1];
                    }
                }
                StringBuilder stringBuilder = new StringBuilder();
                for (String item : path.split("&")) {
                    // 去掉多余的参数
                    if (!StringUtils.containsAny(item, "platform", "workspaceId")) {
                        stringBuilder.append(item);
                        stringBuilder.append("&");
                    }
                }
                path = getProxyPath(stringBuilder.toString());
            }
            // 图片与描述信息之间需换行，否则无法预览图片
            result = "\n\n![" + name + "](" + path + ")";
        }

        return result;
    }

    private String getMatcherResultForImg(String regex, String targetStr) {
        String result = "";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(targetStr);
        while (matcher.find()) {
            result = matcher.group(1);
        }

        return result;
    }

    @Override
    public void getAttachmentContent(String fileKey, Consumer<InputStream> inputStreamHandler) {
        zentaoJsonClient.getAttachmentBytes(fileKey, inputStreamHandler);
    }

    public void syncZentaoIssueAttachments(SyncIssuesResult syncIssuesResult, IssuesWithBLOBs issue) {
        ZentaoRestBugDetailResponse bugInfo = zentaoRestClient.get(issue.getPlatformId());
        Object files = bugInfo.getFiles();
        Map<String, Object> zenFiles;
        if (files instanceof List && ((List) files).size() == 0) {
            zenFiles = null;
        } else {
            zenFiles = (Map) files;
        }
        if (zenFiles != null) {
            Map<String, List<PlatformAttachment>> attachmentMap = syncIssuesResult.getAttachmentMap();
            attachmentMap.put(issue.getId(), new ArrayList<>());
            for (String fileId : zenFiles.keySet()) {
                Map fileInfo = (Map) zenFiles.get(fileId);
                String filename = fileInfo.get("title").toString();
                try {
                    PlatformAttachment syncAttachment = new PlatformAttachment();
                    // name 用于查重
                    syncAttachment.setFileName(filename);
                    // key 用于获取附件内容
                    syncAttachment.setFileKey(fileId);
                    attachmentMap.get(issue.getId()).add(syncAttachment);
                } catch (Exception e) {
                    LogUtil.error(e);
                }

            }
        }
    }

    private Map<String, Object> buildUpdateParam(PlatformIssuesUpdateRequest issuesRequest) {
        issuesRequest.setPlatform(key);
        ZentaoProjectConfig projectConfig = getProjectConfig(issuesRequest.getProjectConfig());
        String projectId = projectConfig.getZentaoId();
        if (StringUtils.isBlank(projectId)) {
            MSPluginException.throwException("未关联禅道项目ID.");
        }
        MultiValueMap<String, Object> multiParamMap = new LinkedMultiValueMap<>();
        //配置项目
        if(StringUtils.isNotBlank(projectId.split("-")[1])) {
            multiParamMap.add("project", projectId.split("-")[1]);
        }
        //配置产品
        multiParamMap.add("product", projectId.split("-")[0]);
        multiParamMap.add("title", issuesRequest.getTitle());
        if (issuesRequest.getTransitions() != null) {
            multiParamMap.add("status", issuesRequest.getTransitions().getValue());
        }

        addCustomFields(issuesRequest, multiParamMap);

        String description = issuesRequest.getDescription();
        String zentaoSteps = description;

        // transfer description
        try {
            zentaoSteps = ms2ZentaoDescription(description, projectId);
            zentaoSteps = zentaoSteps.replaceAll("\\n", "<br/>");
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
        LogUtil.info("zentao description transfer: " + zentaoSteps);

        multiParamMap.add("steps", zentaoSteps);
        Map<String,Object> paramMap=multiParamMap.toSingleValueMap();
        handleBuildParam(paramMap);
        return paramMap;
    }

    private void handleBuildParam(Map<String, Object> paramMap) {
        try {
            Object buildValue = paramMap.get("openedBuild");
            paramMap.remove("openedBuild");
            if (buildValue!=null) {
                List<String> builds= JSON.parseArray(buildValue.toString(), String.class);
                if (CollectionUtils.isNotEmpty(builds)) {
                    builds.forEach(build -> paramMap.put("openedBuild", build));
                } else {
                    paramMap.put("openedBuild", "trunk");
                }
            } else {
                paramMap.put("openedBuild", "trunk");
            }
        } catch (Exception e) {
            LogUtil.error(e);
        }
    }

    private void handleZentaoBugStatus(Map<String, Object> param) {
        if (!param.containsKey("status")) {
            return;
        }
        Object status = param.get("status");
        if (status!=null) {
            return;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String str = (String) status;
            if (StringUtils.equals(str, "resolved")) {
                param.put("resolvedDate", format.format(new Date()));
            } else if (StringUtils.equals(str, "closed")) {
                param.put("closedDate", format.format(new Date()));
                if (!param.containsKey("resolution")) {
                    // 解决方案默认为已解决
                    param.put("resolution", "fixed");
                }
            }
        } catch (Exception e) {
            //
        }
    }

    @Override
    public ResponseEntity proxyForGet(String path, Class responseEntityClazz) {
        return zentaoRestClient.proxyForGet(path, responseEntityClazz);
    }
}
