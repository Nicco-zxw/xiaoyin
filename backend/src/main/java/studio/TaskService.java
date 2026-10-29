package studio;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Files;
import studio.Models.*;

@Service
public class TaskService {
 private final Store db;private final FileStorage files;private final ObjectMapper json;private final TransactionTemplate tx;
 private final boolean simulationEnabled;private final ThreadPoolExecutor executor;
 public TaskService(Store db,FileStorage files,ObjectMapper json,TransactionTemplate tx,
   @Value("${studio.simulation-enabled}") boolean enabled,@Value("${studio.threads}") int threads,@Value("${studio.queue-capacity}") int capacity) {
   this.db=db;this.files=files;this.json=json;this.tx=tx;simulationEnabled=enabled;
   executor=new ThreadPoolExecutor(threads,threads,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),new ThreadPoolExecutor.AbortPolicy());
 }
 @EventListener(ApplicationReadyEvent.class) public void recoverInterrupted(){tx.executeWithoutResult(s->{db.interruptSteps();db.interruptTasks();});}
 @PreDestroy public void stop(){executor.shutdownNow();}
 public record Create(String templateId,List<String> assetIds,Simulation simulation) {}
 public Object create(String user,Create r) {
   var template=CatalogController.requireTemplate(db,r.templateId());
   if(r.assetIds()==null||r.assetIds().size()!=template.requiredImageCount()||new HashSet<>(r.assetIds()).size()!=r.assetIds().size())throw ApiError.bad("素材数量须符合模板要求且不能重复");
   for(String id:r.assetIds()){
     var a=db.asset(id,user);
     if(a==null)throw new ApiError(404,"ASSET_NOT_FOUND","素材不存在或不属于当前用户");
     if(!Files.isRegularFile(files.resolve(a.path()))||a.sizeBytes()>template.maxFileSizeBytes()||!template.getAcceptedMimeTypes().contains(a.mimeType()))throw ApiError.bad("素材不符合模板要求，请重新上传");
   }
   Simulation simulation=r.simulation()==null?Simulation.defaults():r.simulation();
   if(r.simulation()!=null&&!simulationEnabled)throw new ApiError(403,"SIMULATION_DISABLED","模拟控制未启用");
   validateSimulation(simulation);String encoded;
   try{encoded=json.writeValueAsString(simulation);}catch(Exception e){throw ApiError.bad("模拟配置不合法");}
   String id=UUID.randomUUID().toString();
   tx.executeWithoutResult(s->{
     db.addTask(id,user,template,encoded);
     for(int i=0;i<r.assetIds().size();i++)db.link(id,r.assetIds().get(i),i);
     for(String stage:Pipeline.STAGES)db.addStep(id,stage);
   });
   // executeWithoutResult has committed before scheduling. Worker never observes an uncommitted task.
   try{executor.execute(()->run(id));}catch(RejectedExecutionException e){fail(id,null,"EXECUTOR_REJECTED","处理队列已满，请稍后重新制作");}
   return view(require(id,user));
 }
 static void validateSimulation(Simulation s){
   if(s.stageDelayMs()==null||!s.stageDelayMs().keySet().equals(new HashSet<>(Pipeline.STAGES))||s.stageDelayMs().values().stream().anyMatch(v->v==null||v<0||v>60000)||s.failAtStage()!=null&&!Pipeline.STAGES.contains(s.failAtStage()))throw new ApiError(400,"INVALID_SIMULATION","请提供三个阶段的 0–60000ms 延迟及有效失败阶段");
 }
 private void run(String id){
   if(db.claim(id)!=1)return;
   var task=db.task(id);String[] current={null};
   try{
     var simulation=json.readValue(task.simulationJson(),Simulation.class);
     new Pipeline().execute(stage->{
       current[0]=stage;tx.executeWithoutResult(s->{db.stage(id,stage);db.stepStart(id,stage);});
       try{
         Thread.sleep(simulation.stageDelayMs().get(stage));
         if(stage.equals(simulation.failAtStage()))throw new ApiError(400,"SIMULATED_STAGE_FAILURE",stageName(stage)+"按配置模拟失败");
         if(stage.equals("MATERIAL_CHECK"))for(var asset:db.taskAssets(id))if(!Files.isRegularFile(files.resolve(asset.path())))throw ApiError.bad("素材文件已丢失");
         if(stage.equals("RESULT_PACKAGING")){
           String path=files.makeResult(id,task.templateId());
           tx.executeWithoutResult(s->{db.stepEnd(id,stage,"SUCCEEDED");db.success(id,path);});
         }else db.stepEnd(id,stage,"SUCCEEDED");
       }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("服务停止，任务中断",e);}
       catch(java.io.IOException e){throw new IllegalStateException("结果文件整理失败",e);}
     });
   }catch(Exception e){
     org.slf4j.LoggerFactory.getLogger(TaskService.class).warn("Task {} failed at {}",id,current[0],e);
     fail(id,current[0],e instanceof ApiError a?a.code:"PROCESSING_ERROR",e instanceof ApiError?e.getMessage():"处理过程中发生错误，请重新制作");
   }
 }
 private void fail(String id,String stage,String code,String message){tx.executeWithoutResult(s->{if(stage!=null)db.stepEnd(id,stage,"FAILED");db.fail(id,code,message);db.skip(id);});}
 private String stageName(String stage){return switch(stage){case "MATERIAL_CHECK"->"素材检查";case "MOCK_GENERATION"->"模拟生成";default->"结果整理";};}
 public Task require(String id,String user){var task=db.task(id);if(task==null||!task.userId().equals(user))throw new ApiError(404,"TASK_NOT_FOUND","任务不存在");return task;}
 public Object view(Task t){
   var out=new LinkedHashMap<String,Object>();
   out.put("id",t.id());out.put("templateId",t.templateId());out.put("templateTitle",t.templateTitle());out.put("status",t.status());out.put("currentStage",t.currentStage());
   out.put("steps",db.steps(t.id()));out.put("failure",t.failureCode()==null?null:new Failure(t.currentStage(),t.failureCode(),t.failureMessage()));
   out.put("resultUrl","SUCCEEDED".equals(t.status())?"/api/tasks/"+t.id()+"/result":null);
   out.put("createdAt",t.createdAt());out.put("startedAt",t.startedAt());out.put("finishedAt",t.finishedAt());return out;
 }
 record Failure(String stage,String code,String message) {}
}
