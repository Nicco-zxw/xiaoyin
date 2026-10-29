package studio;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

abstract class ApiTestSupport {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 MockHttpSession session;
 @BeforeEach void login() throws Exception { session=registerLogin(); }
 MockHttpSession registerLogin() throws Exception {
   String username="u_"+UUID.randomUUID().toString().replace("-","").substring(0,15);
   mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",username,"password","test-password","confirmPassword","test-password")))).andExpect(status().isCreated());
   var result=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",username,"password","test-password")))).andExpect(status().isOk()).andReturn();
   return (MockHttpSession)result.getRequest().getSession();
 }
 String userId() throws Exception {
   return json.readTree(mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText();
 }
 String upload() throws Exception {
   byte[] data=new org.springframework.core.io.ClassPathResource("demo/sample.png").getContentAsByteArray();
   var result=mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","sample.png","image/png",data)).param("templateId","pet").session(session).with(csrf())).andExpect(status().isCreated()).andReturn();
   return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
 }
 JsonNode create(String asset,String stage,int delay) throws Exception {
   var simulation=new HashMap<String,Object>();simulation.put("stageDelayMs",Map.of("MATERIAL_CHECK",delay,"MOCK_GENERATION",delay,"RESULT_PACKAGING",delay));simulation.put("failAtStage",stage);
   var response=mvc.perform(post("/api/tasks").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("templateId","pet","assetIds",List.of(asset),"simulation",simulation)))).andExpect(status().isAccepted()).andReturn();
   return json.readTree(response.getResponse().getContentAsString());
 }
 JsonNode task(String id) throws Exception {return json.readTree(mvc.perform(get("/api/tasks/"+id).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 JsonNode await(String id) throws Exception {
   for(int i=0;i<200;i++){var t=task(id);if(List.of("SUCCEEDED","FAILED").contains(t.get("status").asText()))return t;Thread.sleep(25);}
   throw new AssertionError("task did not finish: "+id);
 }
}
