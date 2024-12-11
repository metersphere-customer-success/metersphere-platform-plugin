
import io.metersphere.platform.client.BaseZentaoJsonClient;
import io.metersphere.platform.client.ZentaoRestClient;
import io.metersphere.platform.client.ZentaoFactory;
import io.metersphere.platform.domain.AddIssueResponse;
import io.metersphere.platform.domain.PlatformRequest;
import io.metersphere.platform.domain.SelectOption;
import io.metersphere.platform.domain.ZentaoConfig;
import io.metersphere.platform.domain.response.rest.ZentaoRestBugDetailResponse;
import io.metersphere.platform.domain.response.rest.ZentaoRestDemandResponse;
import io.metersphere.platform.domain.response.rest.ZentaoRestUserResponse;
import io.metersphere.platform.impl.ZentaoPlatform;
import io.metersphere.plugin.utils.JSON;
import io.metersphere.plugin.utils.LogUtil;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testng.annotations.BeforeClass;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @program: metersphere-platform-plugin
 * @ClassName: Test
 * @description:
 */

public class Test {

  protected ZentaoRestClient zentaorestClient;
  protected BaseZentaoJsonClient zentaoJsonClient;
  protected ZentaoPlatform zentaoPlatform;
  protected ZentaoConfig zentaoConfig=new ZentaoConfig();




  @BeforeClass
    public void createClient(){

    zentaoConfig.setAccount("admin");
    zentaoConfig.setPassword("Calong@2015");
    zentaoConfig.setUrl("http://10.1.13.49:80/");
    zentaoConfig.setRequestType("PATH_INFO");


    zentaorestClient = new ZentaoRestClient(zentaoConfig.getUrl());
    zentaoJsonClient =ZentaoFactory.getInstance(zentaoConfig.getUrl(),zentaoConfig.getRequestType());
    zentaorestClient.initConfig(zentaoConfig);
    zentaoJsonClient.initConfig(zentaoConfig);


      PlatformRequest request=new PlatformRequest();
      request.setIntegrationConfig(JSON.toJSONString(zentaoConfig));
      zentaoPlatform=new ZentaoPlatform(request);
      //zentaoPlatform=new ZentaoPlatform();


  }
   @org.testng.annotations.Test
  public void validateProjectConfig() {
   // zentaorestClient.checkProjectExist("1","products");
       zentaorestClient.checkProjectExist("1","projects");
  }
//  @org.testng.annotations.Test
//  public void testGetBugs(){
//
//    List<PlatformBugDTO> needSyncBugs = new ArrayList<>();
//    Map<String, Object> bugResponseMap = zentaoClient.getBugsByProjectId(0, 0, "1");
//    List<?> zentaoBugs = (List<?>) bugResponseMap.get("bugs");
//    int currentSize = zentaoBugs.size();
//    System.out.println("禅道缺陷数据："+currentSize);
//
//  }
   @org.testng.annotations.Test
  public void addissue(){
     Map<String, Object> paramMap = new LinkedHashMap<>();
     paramMap.put("project","2");
     paramMap.put("title","11测试产品级项目");
     paramMap.put("product","2");
     paramMap.put("severity","1");
     paramMap.put("pri","1");
     paramMap.put("type","codeerror");
       List<String> list=new ArrayList<>();
       list.add("主干");
       paramMap.put("openedBuild",list);
       System.out.println("addIssue请求参数："+ JSON.toJSONString(paramMap));
      // AddIssueResponse.Issue issue = zentaorestClient.addIssue(paramMap);
      // System.out.println("所属项目："+issue.getProject());
       //System.out.println("issue id："+issue.getId());
  }

  @org.testng.annotations.Test
    public void updateIssue(){
      Map<String, Object> paramMap1 = new LinkedHashMap<>();

      paramMap1.put("project","4");


      paramMap1.put("title","MSv2.10.10缺陷");
      paramMap1.put("severity","2");
      paramMap1.put("pri","2");
      paramMap1.put("type","codeerror");
      List<String> list=new ArrayList<>();
      list.add("主干");
      paramMap1.put("openedBuild",list);
     // paramMap1.put("status","resolved");
      zentaorestClient.updateIssue("46",paramMap1);

  }
  @org.testng.annotations.Test
    public void deleteissue(){
      zentaorestClient.deleteIssue("30");
  }
  @org.testng.annotations.Test
  public void getbuf(){
      ZentaoRestBugDetailResponse isuue=zentaorestClient.get("53");
      System.out.println(isuue);
  }
  @org.testng.annotations.Test
  public void getCreateMetaData(){
    zentaoJsonClient.getCreateMetaData("1",zentaorestClient);
  }
  @org.testng.annotations.Test
  public void servicein(){
    zentaorestClient.auth();
  }

  @org.testng.annotations.Test
  public void getBugByProject(){
    Map<String, Object> response = zentaoJsonClient.getBugsByProductId( 1, 200,"1",zentaorestClient);
    LinkedHashMap<String,LinkedHashMap>  zentaoIssues = (LinkedHashMap<String,LinkedHashMap>) response.get("bugs");

    List<Map>   issues=new ArrayList<Map>(zentaoIssues.values());
      System.out.println("数量："+issues.size());
       issues = issues.stream().filter(map -> ( map.get("project").toString().equals("4"))).collect(Collectors.toList());


      //zentaoIssues.forEach((key, value) -> System.out.println("Key = " + key + ", Value = " + value));
   List<String> allIds = issues.stream().map(i -> i.get("id").toString()).collect(Collectors.toList());

      System.out.println("筛选后的数量:"+issues.size());

//
//    for(String id:allIds){
//      System.out.println(id);
//    }

  }
  @org.testng.annotations.Test
   public void getsession(){
    System.out.println(zentaoJsonClient.auth());
   }

   @org.testng.annotations.Test
    public void testZenPlatform(){
       PlatformRequest request=new PlatformRequest();
       ZentaoPlatform zentaoPlatform=new ZentaoPlatform(request);

   }
   @org.testng.annotations.Test()
    public void testMethod(){
//       MultiValueMap<String, Object> paramMap1 = new LinkedMultiValueMap<>();
//       paramMap1.add("project","1");
//       paramMap1.add("title","MSv2.10.10缺陷");
//       paramMap1.add("severity","2");
//       paramMap1.add("pri","2");
//       paramMap1.add("type","codeerror");
//     Map<String,Object> map=paramMap1.toSingleValueMap();
//     map.forEach((key, value) -> System.out.println("Key = " + key + ", Value = " + value));


       //System.out.println(zentaoJsonClient.requestUrl.getReplaceImgUrl());

//       String id="1111-";
//       String[] arr=id.split("-");
//       for (int i = 0; i < arr.length; i++) {
//           System.out.println(i+"===="+arr[i]);
//       }
//
//       System.out.println("对对对对对："+(StringUtils.isNotBlank(id.split("-")[1])?id.split("-")[1]:id.split("-")[0]));
//

//  int[] arr=new int[]{1,2,3,4};
//       for (int i = 0; i < arr.length; i++) {
//           if (i==2){
//               System.out.println("2222");
//           //    break;
//               continue;
//           }
//           System.out.println(i);
//       }
       MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
       paramMap.add("account", "admin");
       paramMap.add("password", "Calong@2015");
       System.out.println(JSON.toJSONString(paramMap));


  }

   @org.testng.annotations.Test
    public void getDemand(){
       List<ZentaoRestDemandResponse.Story> stories=zentaorestClient.getDemands("2","products").getStories();
       System.out.println("产品1："+stories);
       List<ZentaoRestDemandResponse.Story> stories1= zentaorestClient.getDemands("1","projects").getStories();
       System.out.println("项目1"+stories1);

   }
   @org.testng.annotations.Test
    public void getUser(){
       ZentaoRestUserResponse users = zentaorestClient.getUsers(1, Integer.MAX_VALUE);
       List<SelectOption> userOptions = users.getUsers().stream().map(user -> new SelectOption(user.getRealname(), user.getAccount())).collect(Collectors.toList());
       System.out.println("user:"+users);
   }
    @org.testng.annotations.Test
    public void testImg(){

        ResponseEntity<byte[]> response=zentaorestClient.proxyForGet("/file-read-10.jpg&", byte[].class);
        System.out.println(response);
    }
    @org.testng.annotations.Test
    public void testattach(){
        File file=new File("/Users/test/Desktop/test.html");
        if(file.exists()) {
            zentaoJsonClient.uploadAttachment("bug", "53", file);
        }
    }

    @org.testng.annotations.Test
    public void closeBug(){
      zentaorestClient.closeBug("46");
    }
    @org.testng.annotations.Test
    public void activeBug(){
        ResponseEntity response= zentaorestClient.activeBug("45","lijx");
        System.out.println(response);
    }

    @org.testng.annotations.Test
    public void  resolveBug(){
      zentaorestClient.resolveBug("45","lijx","bydesign");
    }


}
