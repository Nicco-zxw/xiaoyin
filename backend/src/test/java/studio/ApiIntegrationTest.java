package studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="INTEGRATION_TEST",matches="true")
class ApiIntegrationTest extends ApiTestSupport {
 @Test void succeedsAndPersistsResultAndDoesNotRegress() throws Exception {
   String id=create(upload(),null,20).get("id").asText();var done=await(id);
   assertThat(done.get("status").asText()).isEqualTo("SUCCEEDED");
   for(var step:done.get("steps"))assertThat(step.get("status").asText()).isEqualTo("SUCCEEDED");
   mvc.perform(get("/api/tasks/"+id+"/result").session(session)).andExpect(status().isOk()).andExpect(content().contentType("image/svg+xml"));
   assertThat(task(id).get("status").asText()).isEqualTo("SUCCEEDED");
 }
 @Test void eachFailurePersistsAndSkipsRemainingStages() throws Exception {
   String asset=upload();
   for(int index=0;index<Pipeline.STAGES.size();index++){
     String stage=Pipeline.STAGES.get(index),id=create(asset,stage,0).get("id").asText();var done=await(id);
     assertThat(done.get("status").asText()).isEqualTo("FAILED");assertThat(done.at("/failure/stage").asText()).isEqualTo(stage);
     for(int i=0;i<3;i++)assertThat(done.at("/steps/"+i+"/status").asText()).isEqualTo(i<index?"SUCCEEDED":i==index?"FAILED":"SKIPPED");
   }
 }
 @Test void returnsBeforeWorkCompletesAndIsolatesUsers() throws Exception {
   String asset=upload();long start=System.nanoTime();var created=create(asset,null,1000);long elapsed=(System.nanoTime()-start)/1_000_000;
   assertThat(elapsed).isLessThan(2500);assertThat(created.get("status").asText()).isIn("QUEUED","RUNNING");
   String id=created.get("id").asText();MockHttpSession other=registerLogin();
   mvc.perform(get("/api/tasks/"+id).session(other)).andExpect(status().isNotFound());
   mvc.perform(get("/api/assets/"+asset+"/content").session(other)).andExpect(status().isNotFound());
   mvc.perform(get("/api/tasks/"+id+"/result").session(other)).andExpect(status().isNotFound());
   assertThat(await(id).get("status").asText()).isEqualTo("SUCCEEDED");
 }
 @Test void rejectsInvalidCountsAndFakeImage() throws Exception {
   mvc.perform(post("/api/tasks").session(session).with(csrf()).contentType("application/json").content("{\"templateId\":\"pet\",\"assetIds\":[]}")).andExpect(status().isBadRequest());
   mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","fake.png","image/png","not a picture".getBytes())).param("templateId","pet").session(session).with(csrf())).andExpect(status().isUnsupportedMediaType());
 }
 @Test void requiresCsrfAndChangingPasswordExpiresOtherSessions() throws Exception {
   mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
   String username=json.readTree(mvc.perform(get("/api/auth/me").session(session)).andReturn().getResponse().getContentAsString()).get("username").asText();
   var result=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",username,"password","test-password")))).andExpect(status().isOk()).andReturn();
   var second=(MockHttpSession)result.getRequest().getSession();
   mvc.perform(put("/api/auth/password").session(session).with(csrf()).contentType("application/json").content("{\"currentPassword\":\"test-password\",\"newPassword\":\"changed-password\",\"confirmPassword\":\"changed-password\"}")).andExpect(status().isNoContent());
   mvc.perform(get("/api/auth/me").session(second)).andExpect(status().isUnauthorized());
   mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",username,"password","changed-password")))).andExpect(status().isOk());
 }
}
