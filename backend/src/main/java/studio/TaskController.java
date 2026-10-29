package studio;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import java.util.*;
import java.net.URI;

@RestController @RequestMapping("/api/tasks")
public class TaskController {
 private final TaskService tasks;private final Store db;private final FileStorage files;
 public TaskController(TaskService tasks,Store db,FileStorage files){this.tasks=tasks;this.db=db;this.files=files;}
 @PostMapping ResponseEntity<?> create(@RequestBody TaskService.Create r,Authentication a){
   Object view=tasks.create(a.getName(),r);String id=(String)((Map<?,?>)view).get("id");return ResponseEntity.accepted().location(URI.create("/api/tasks/"+id)).body(view);
 }
 @GetMapping("/{id}") Object task(@PathVariable String id,Authentication a){return tasks.view(tasks.require(id,a.getName()));}
 @GetMapping Object history(@RequestParam(required=false)String templateId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,Authentication a){
   if(page<0||page>100000||size<1||size>100)throw ApiError.bad("分页参数不合法");
   long total=db.count(a.getName(),templateId);
   return Map.of("items",db.history(a.getName(),templateId,size,page*size).stream().map(tasks::view).toList(),"page",page,"size",size,"totalElements",total,"totalPages",(total+size-1)/size);
 }
 @GetMapping("/active") Object active(Authentication a){return Map.of("items",db.active(a.getName()).stream().map(tasks::view).toList());}
 @GetMapping("/{id}/result") ResponseEntity<Resource> result(@PathVariable String id,Authentication a){
   var task=tasks.require(id,a.getName());if(!task.status().equals("SUCCEEDED"))throw new ApiError(409,"RESULT_NOT_READY","任务尚未成功完成");
   return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/svg+xml")).cacheControl(CacheControl.noStore()).body(files.resource(task.resultPath()));
 }
}
