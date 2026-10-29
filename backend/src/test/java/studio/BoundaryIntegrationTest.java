package studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import studio.Models.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Files;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="INTEGRATION_TEST",matches="true")
class BoundaryIntegrationTest extends ApiTestSupport {
 @Autowired Store db;
 @Autowired FileStorage files;
 @Autowired TransactionTemplate tx;
 @Autowired JdbcTemplate jdbc;
 @Value("${studio.storage}") String storagePath;

 @Test void queueRejectionIsPersistedWithoutDisturbingRunningAndQueuedTasks() throws Exception {
   var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
   // Block real result work, not a wall-clock guess about when a worker might start.
   var storage=new FileStorage(storagePath){
     @Override public String makeResult(String task,String template) throws IOException {
       entered.countDown();
       try{if(!release.await(10,TimeUnit.SECONDS))throw new IOException("test gate timeout");}
       catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
       return super.makeResult(task,template);
     }
   };
   var worker=new TaskService(db,storage,json,tx,true,1,1);
   String user=userId(),asset=upload();var ids=new ArrayList<String>();
   var request=new TaskService.Create("pet",List.of(asset),new Simulation(Map.of("MATERIAL_CHECK",0,"MOCK_GENERATION",0,"RESULT_PACKAGING",0),null));
   try{
     String first=(String)((Map<?,?>)worker.create(user,request)).get("id");ids.add(first);
     assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
     String queued=(String)((Map<?,?>)worker.create(user,request)).get("id");ids.add(queued);
     String rejected=(String)((Map<?,?>)worker.create(user,request)).get("id");
     assertThat(task(first).get("status").asText()).isEqualTo("RUNNING");
     assertThat(task(queued).get("status").asText()).isEqualTo("QUEUED");
     var failed=task(rejected);
     assertThat(failed.get("status").asText()).isEqualTo("FAILED");
     assertThat(failed.at("/failure/code").asText()).isEqualTo("EXECUTOR_REJECTED");
     assertThat(failed.get("currentStage").isNull()).isTrue();
     assertThat(failed.get("finishedAt").isNull()).isFalse();
     assertThat(failed.get("resultUrl").isNull()).isTrue();
     for(var step:failed.get("steps"))assertThat(step.get("status").asText()).isEqualTo("SKIPPED");
     release.countDown();
     assertThat(await(first).get("status").asText()).isEqualTo("SUCCEEDED");
     assertThat(await(queued).get("status").asText()).isEqualTo("SUCCEEDED");
     assertThat(task(rejected).get("status").asText()).isEqualTo("FAILED");
   }finally{
     release.countDown();
     try{for(String id:ids)await(id);}finally{worker.stop();}
   }
 }

 @Test void simultaneousTasksKeepTheirOwnFailureAndDelayConfiguration() throws Exception {
   String asset=upload();
   String success=create(asset,null,120).get("id").asText();
   String failure=create(asset,"MOCK_GENERATION",0).get("id").asText();
   var failed=await(failure);var done=await(success);
   assertThat(done.get("status").asText()).isEqualTo("SUCCEEDED");
   assertThat(done.get("failure").isNull()).isTrue();assertThat(done.get("resultUrl").isTextual()).isTrue();
   assertThat(failed.get("status").asText()).isEqualTo("FAILED");
   assertThat(failed.at("/failure/stage").asText()).isEqualTo("MOCK_GENERATION");
   assertThat(failed.at("/steps/2/status").asText()).isEqualTo("SKIPPED");
   assertThat(failed.get("resultUrl").isNull()).isTrue();
   assertThat(json.readTree(db.task(success).simulationJson()).at("/stageDelayMs/MOCK_GENERATION").asInt()).isEqualTo(120);
   assertThat(json.readTree(db.task(failure).simulationJson()).at("/stageDelayMs/MOCK_GENERATION").asInt()).isZero();
 }

 @Test void historyPaginationIsStableAtTimestampTiesAndFiltersBeforeCounting() throws Exception {
   String user=userId();var expected=new ArrayList<String>();
   tx.executeWithoutResult(s->{
     for(int i=0;i<123;i++){
       String id=UUID.randomUUID().toString();expected.add(id);
       db.addTask(id,user,db.template(i%2==0?"pet":"portrait"),"{}");
       for(String stage:Pipeline.STAGES)db.addStep(id,stage);
       db.fail(id,"TEST_FIXTURE","分页测试记录");db.skip(id);
     }
     jdbc.update("UPDATE creation_tasks SET created_at='2026-01-01 00:00:00.000' WHERE user_id=?",user);
   });
   expected.sort(Comparator.reverseOrder());var actual=new ArrayList<String>();
   for(int page=0;page<5;page++){
     var response=history("?page="+page+"&size=25");
     assertThat(response.get("totalElements").asInt()).isEqualTo(123);
     assertThat(response.get("totalPages").asInt()).isEqualTo(5);
     assertThat(response.get("items").size()).isEqualTo(page==4?23:25);
     for(var item:response.get("items"))actual.add(item.get("id").asText());
   }
   assertThat(actual).doesNotHaveDuplicates().containsExactlyElementsOf(expected);
   assertThat(history("?page=5&size=25").get("items").isEmpty()).isTrue();
   var filtered=history("?templateId=pet&size=100");
   assertThat(filtered.get("totalElements").asInt()).isEqualTo(62);
   for(var item:filtered.get("items"))assertThat(item.get("templateId").asText()).isEqualTo("pet");
   assertThat(history("?templateId=missing").get("totalElements").asInt()).isZero();
   var other=registerLogin();
   var empty=json.readTree(mvc.perform(get("/api/tasks").session(other)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
   assertThat(empty.get("totalElements").asInt()).isZero();
   for(String query:List.of("?page=-1","?page=100001","?size=0","?size=101"))mvc.perform(get("/api/tasks"+query).session(session)).andExpect(status().isBadRequest());
 }
 private JsonNode history(String query) throws Exception {
   return json.readTree(mvc.perform(get("/api/tasks"+query).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
 }

 @Test void missingFilesDoNotCreateTasksOrRegressCompletedTasks() throws Exception {
   String asset=upload(),user=userId();
   String id=create(asset,null,0).get("id").asText();assertThat(await(id).get("status").asText()).isEqualTo("SUCCEEDED");
   Files.delete(files.resolve(db.task(id).resultPath()));
   mvc.perform(get("/api/tasks/"+id+"/result").session(session)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESULT_FILE_MISSING"));
   assertThat(task(id).get("status").asText()).isEqualTo("SUCCEEDED");
   Files.delete(files.resolve(db.asset(asset,user).path()));long count=db.count(user,null);
   mvc.perform(post("/api/tasks").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("templateId","pet","assetIds",List.of(asset))))).andExpect(status().isBadRequest());
   assertThat(db.count(user,null)).isEqualTo(count);
 }

 @Test void uploadRejectsOverLimitDimensionsAndIgnoresPathInOriginalFilename() throws Exception {
   mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","large.png","image/png",new byte[5*1024*1024+1])).param("templateId","pet").session(session).with(csrf())).andExpect(status().isPayloadTooLarge());
   var image=new BufferedImage(8193,1,BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
   mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","wide.png","image/png",bytes.toByteArray())).param("templateId","pet").session(session).with(csrf())).andExpect(status().isBadRequest());
   byte[] valid=new org.springframework.core.io.ClassPathResource("demo/sample.png").getContentAsByteArray();
   var response=mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","../../escape.png","image/png",valid)).param("templateId","pet").session(session).with(csrf())).andExpect(status().isCreated()).andReturn();
   String id=json.readTree(response.getResponse().getContentAsString()).get("id").asText();
   assertThat(db.asset(id,userId()).path()).isEqualTo("uploads/"+id);
   assertThat(Files.isRegularFile(files.resolve("uploads/"+id))).isTrue();
 }
 @Test void malformedPaginationAndMissingUploadFieldsAreClientErrors() throws Exception {
   for(String query:List.of("?page=abc","?page=1.5","?size=2147483648"))mvc.perform(get("/api/tasks"+query).session(session)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
   byte[] valid=new org.springframework.core.io.ClassPathResource("demo/sample.png").getContentAsByteArray();
   mvc.perform(multipart("/api/assets").file(new MockMultipartFile("file","sample.png","image/png",valid)).session(session).with(csrf())).andExpect(status().isBadRequest());
   mvc.perform(multipart("/api/assets").param("templateId","pet").session(session).with(csrf())).andExpect(status().isBadRequest());
 }
}
