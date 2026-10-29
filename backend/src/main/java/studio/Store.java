package studio;

import org.apache.ibatis.annotations.*;
import java.util.*;
import studio.Models.*;

@Mapper
public interface Store {
 @Select("SELECT * FROM users WHERE username=#{name}") User userByName(String name);
 @Select("SELECT * FROM users WHERE id=#{id}") User user(String id);
 @Insert("INSERT INTO users(id,username,password_hash) VALUES(#{id},#{name},#{hash})") void addUser(String id,String name,String hash);
 @Update("UPDATE users SET password_hash=#{hash} WHERE id=#{id}") void password(String id,String hash);
 @Select("SELECT * FROM template_categories ORDER BY sort_order") List<Category> categories();
 @Select("<script>SELECT * FROM templates <if test='category != null'>WHERE category_id=#{category}</if> ORDER BY id</script>") List<Template> templates(String category);
 @Select("SELECT * FROM templates WHERE id=#{id}") Template template(String id);
 @Insert("INSERT INTO assets(id,user_id,path,mime_type,size_bytes,width,height) VALUES(#{id},#{userId},#{path},#{mimeType},#{sizeBytes},#{width},#{height})") void addAsset(Asset a);
 @Select("SELECT * FROM assets WHERE id=#{id} AND user_id=#{user}") Asset asset(String id,String user);
 @Insert("INSERT INTO creation_tasks(id,user_id,template_id,template_title,status,simulation_json) VALUES(#{id},#{user},#{template.id},#{template.title},'QUEUED',#{simulation})") void addTask(String id,String user,Template template,String simulation);
 @Insert("INSERT INTO task_assets VALUES(#{task},#{asset},#{slot})") void link(String task,String asset,int slot);
 @Insert("INSERT INTO task_step_runs(task_id,stage,status) VALUES(#{task},#{stage},'PENDING')") void addStep(String task,String stage);
 @Select("SELECT * FROM creation_tasks WHERE id=#{id}") Task task(String id);
 @Select("SELECT stage,status,started_at,finished_at FROM task_step_runs WHERE task_id=#{id} ORDER BY FIELD(stage,'MATERIAL_CHECK','MOCK_GENERATION','RESULT_PACKAGING')") List<Step> steps(String id);
 @Select("SELECT a.* FROM assets a JOIN task_assets ta ON a.id=ta.asset_id WHERE ta.task_id=#{task} ORDER BY slot_index") List<Asset> taskAssets(String task);
 @Select("<script>SELECT * FROM creation_tasks WHERE user_id=#{user}<if test='template != null'> AND template_id=#{template}</if> ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}</script>") List<Task> history(String user,String template,int size,int offset);
 @Select("<script>SELECT COUNT(*) FROM creation_tasks WHERE user_id=#{user}<if test='template != null'> AND template_id=#{template}</if></script>") long count(String user,String template);
 @Select("SELECT * FROM creation_tasks WHERE user_id=#{user} AND status IN ('QUEUED','RUNNING') ORDER BY created_at,id") List<Task> active(String user);
 @Update("UPDATE creation_tasks SET status='RUNNING',started_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status='QUEUED'") int claim(String id);
 @Update("UPDATE creation_tasks SET current_stage=#{stage} WHERE id=#{id} AND status='RUNNING'") void stage(String id,String stage);
 @Update("UPDATE task_step_runs SET status='RUNNING',started_at=CURRENT_TIMESTAMP(3) WHERE task_id=#{id} AND stage=#{stage}") void stepStart(String id,String stage);
 @Update("UPDATE task_step_runs SET status=#{status},finished_at=CURRENT_TIMESTAMP(3) WHERE task_id=#{id} AND stage=#{stage}") void stepEnd(String id,String stage,String status);
 @Update("UPDATE creation_tasks SET status='SUCCEEDED',result_path=#{path},finished_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status='RUNNING'") void success(String id,String path);
 @Update("UPDATE creation_tasks SET status='FAILED',failure_code=#{code},failure_message=#{message},finished_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status IN ('QUEUED','RUNNING')") void fail(String id,String code,String message);
 @Update("UPDATE task_step_runs SET status='SKIPPED' WHERE task_id=#{id} AND status='PENDING'") void skip(String id);
 @Update("UPDATE task_step_runs s JOIN creation_tasks t ON t.id=s.task_id SET s.status=CASE WHEN s.status='RUNNING' THEN 'FAILED' ELSE 'SKIPPED' END,s.finished_at=CURRENT_TIMESTAMP(3) WHERE t.status IN ('QUEUED','RUNNING') AND s.status IN ('PENDING','RUNNING')") void interruptSteps();
 @Update("UPDATE creation_tasks SET status='FAILED',failure_code='SERVICE_RESTARTED',failure_message='服务重启导致任务中断，请重新制作',finished_at=CURRENT_TIMESTAMP(3) WHERE status IN ('QUEUED','RUNNING')") void interruptTasks();
}
