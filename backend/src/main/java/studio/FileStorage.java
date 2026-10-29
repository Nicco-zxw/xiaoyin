package studio;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.*;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.io.*;
import studio.Models.*;
import java.util.*;

@Component
public class FileStorage {
 private final Path root;
 public FileStorage(@Value("${studio.storage}") String root) throws IOException { this.root=Path.of(root).toAbsolutePath().normalize();Files.createDirectories(this.root); }
 public Asset upload(MultipartFile file,String user,Template template) throws IOException {
   if(file.isEmpty()) throw ApiError.bad("请选择图片");
   if(file.getSize()>template.maxFileSizeBytes()) throw new ApiError(413,"FILE_TOO_LARGE","图片不能超过 5MB");
   String id=UUID.randomUUID().toString(); Path path=resolve("uploads/"+id);
   Files.createDirectories(path.getParent());
   try(var in=file.getInputStream()){Files.copy(in,path);}
   try(var input=ImageIO.createImageInputStream(path.toFile())) {
     var readers=ImageIO.getImageReaders(input);
     if(!readers.hasNext()) throw new ApiError(415,"UNSUPPORTED_IMAGE_TYPE","只支持有效的 JPEG 或 PNG 图片");
     var reader=readers.next();
     try {
       String format=reader.getFormatName().toLowerCase(Locale.ROOT);
       if(!Set.of("jpeg","jpg","png").contains(format)) throw new ApiError(415,"UNSUPPORTED_IMAGE_TYPE","只支持 JPEG 或 PNG 图片");
       reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);
       if(width>8192||height>8192||(long)width*height>40_000_000) throw ApiError.bad("图片尺寸过大，单边最多 8192 像素，总像素最多 4000 万");
       if(reader.read(0)==null) throw ApiError.bad("图片无法解码");
       return new Asset(id,user,"uploads/"+id,format.equals("png")?"image/png":"image/jpeg",file.getSize(),width,height,null);
     } finally { reader.dispose(); }
   } catch(Exception e) { Files.deleteIfExists(path); if(e instanceof ApiError a)throw a;throw ApiError.bad("图片损坏或无法读取"); }
 }
 public Path resolve(String relative) {
   Path p=root.resolve(relative).normalize();if(!p.startsWith(root))throw ApiError.bad("文件路径不合法");return p;
 }
 public Resource resource(String path) { if(!Files.isRegularFile(resolve(path)))throw new ApiError(404,"RESULT_FILE_MISSING","文件不存在");return new FileSystemResource(resolve(path)); }
 public String makeResult(String task,String template) throws IOException {
   String relative="results/"+task+".svg";Path target=resolve(relative);Files.createDirectories(target.getParent());
   Path tmp=Files.createTempFile(target.getParent(),"result-",".tmp");
   try(var in=new ClassPathResource("demo/"+template+".svg").getInputStream()) {
     Files.copy(in,tmp,StandardCopyOption.REPLACE_EXISTING);Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
   } finally { Files.deleteIfExists(tmp); }
   return relative;
 }
}
