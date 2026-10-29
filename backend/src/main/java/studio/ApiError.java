package studio;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.util.*;

public class ApiError extends RuntimeException {
 final int status; final String code;
 public ApiError(int status,String code,String message) { super(message);this.status=status;this.code=code; }
 public static ApiError bad(String message) { return new ApiError(400,"VALIDATION_ERROR",message); }
 public static Map<String,Object> body(String code,String message) {
   return Map.of("code",code,"message",message,"fieldErrors",Map.of(),"requestId",UUID.randomUUID().toString());
 }
 @RestControllerAdvice
 public static class Handler {
   @ExceptionHandler(ApiError.class) ResponseEntity<?> api(ApiError e) { return ResponseEntity.status(e.status).body(body(e.code,e.getMessage())); }
   @ExceptionHandler(DuplicateKeyException.class) ResponseEntity<?> duplicate() { return ResponseEntity.status(409).body(body("USERNAME_TAKEN","用户名已被使用")); }
   @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> size() { return ResponseEntity.status(413).body(body("FILE_TOO_LARGE","图片不能超过 5MB")); }
   @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> json() { return ResponseEntity.badRequest().body(body("VALIDATION_ERROR","请求格式不正确或包含未知字段")); }
   @ExceptionHandler({MethodArgumentTypeMismatchException.class,ServletRequestBindingException.class,MissingServletRequestPartException.class})
   ResponseEntity<?> parameters() { return ResponseEntity.badRequest().body(body("VALIDATION_ERROR","请求参数缺失或格式不正确")); }
   @ExceptionHandler(Exception.class) ResponseEntity<?> unknown(Exception e) {
     org.slf4j.LoggerFactory.getLogger(Handler.class).error("Unhandled API error",e);
     return ResponseEntity.internalServerError().body(body("INTERNAL_ERROR","服务暂时不可用，请稍后重试"));
   }
 }
}
