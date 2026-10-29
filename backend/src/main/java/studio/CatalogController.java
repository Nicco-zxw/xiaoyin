package studio;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.io.IOException;

@RestController @RequestMapping("/api")
public class CatalogController {
 private final Store db;private final FileStorage files;
 public CatalogController(Store db,FileStorage files){this.db=db;this.files=files;}
 @GetMapping("/categories") Object categories(){return Map.of("items",db.categories());}
 @GetMapping("/templates") Object templates(@RequestParam(required=false) String categoryId){return Map.of("items",db.templates(categoryId));}
 @GetMapping("/templates/{id}") Object template(@PathVariable String id){return requireTemplate(db,id);}
 static Models.Template requireTemplate(Store db,String id){var t=db.template(id);if(t==null)throw new ApiError(404,"TEMPLATE_NOT_FOUND","模板不存在");return t;}
 @GetMapping("/demo-files/{name}") ResponseEntity<Resource> demo(@PathVariable String name){
   if(!Set.of("postcard.svg","pet.svg","portrait.svg","duo.svg","product.svg","moodboard.svg","sample.png").contains(name))throw new ApiError(404,"NOT_FOUND","示例文件不存在");
   return ResponseEntity.ok().contentType(MediaType.parseMediaType(name.endsWith("png")?"image/png":"image/svg+xml")).body(new ClassPathResource("demo/"+name));
 }
 @PostMapping("/assets") @ResponseStatus(HttpStatus.CREATED)
 Object upload(@RequestParam String templateId,@RequestParam MultipartFile file,Authentication a) throws IOException {
   var asset=files.upload(file,a.getName(),requireTemplate(db,templateId));db.addAsset(asset);
   return Map.of("id",asset.id(),"previewUrl","/api/assets/"+asset.id()+"/content","mimeType",asset.mimeType(),"sizeBytes",asset.sizeBytes(),"width",asset.width(),"height",asset.height(),"createdAt",db.asset(asset.id(),a.getName()).createdAt());
 }
 @GetMapping("/assets/{id}/content") ResponseEntity<Resource> content(@PathVariable String id,Authentication a){
   var asset=db.asset(id,a.getName());if(asset==null)throw new ApiError(404,"ASSET_NOT_FOUND","素材不存在");
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(asset.mimeType())).body(files.resource(asset.path()));
 }
}
