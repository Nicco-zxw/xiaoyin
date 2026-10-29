package studio;

import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final Store db; private final PasswordEncoder encoder; private final SecurityContextRepository contexts;
 private final SessionAuthenticationStrategy strategy; private final SessionRegistry sessions;
 public AuthController(Store db,PasswordEncoder encoder,SecurityContextRepository contexts,SessionAuthenticationStrategy strategy,SessionRegistry sessions) {
   this.db=db;this.encoder=encoder;this.contexts=contexts;this.strategy=strategy;this.sessions=sessions;
 }
 public record Register(String username,String password,String confirmPassword) {}
 public record Login(String username,String password) {}
 public record Password(String currentPassword,String newPassword,String confirmPassword) {}
 @GetMapping("/csrf") Object csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
 Object register(@RequestBody Register r) {
   String name=normalize(r.username()); validatePassword(r.password(),r.confirmPassword());
   String id=UUID.randomUUID().toString();db.addUser(id,name,encoder.encode(r.password()));return publicUser(db.user(id));
 }
 @PostMapping("/login") Object login(@RequestBody Login r,HttpServletRequest req,HttpServletResponse res) {
   String name=r.username()==null?"":r.username().trim().toLowerCase(Locale.ROOT);
   var user=db.userByName(name);
   // Perform a BCrypt check even for missing usernames.
   String hash=user==null?"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy":user.passwordHash();
   boolean valid=encoder.matches(r.password()==null?"":r.password(),hash);
   if(user==null||!valid) throw new ApiError(401,"INVALID_CREDENTIALS","用户名或密码不正确");
   var auth=UsernamePasswordAuthenticationToken.authenticated(user.id(),null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
   strategy.onAuthentication(auth,req,res);
   var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);SecurityContextHolder.setContext(context);contexts.saveContext(context,req,res);
   return publicUser(user);
 }
 @GetMapping("/me") Object me(Authentication a) { return publicUser(db.user(a.getName())); }
 @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) void logout(HttpServletRequest req) {
   var session=req.getSession(false);if(session!=null){sessions.removeSessionInformation(session.getId());session.invalidate();}SecurityContextHolder.clearContext();
 }
 @PutMapping("/password") @ResponseStatus(HttpStatus.NO_CONTENT)
 void password(@RequestBody Password p,Authentication a,HttpServletRequest req) {
   validatePassword(p.newPassword(),p.confirmPassword());
   if(!encoder.matches(p.currentPassword()==null?"":p.currentPassword(),db.user(a.getName()).passwordHash())) throw new ApiError(400,"CURRENT_PASSWORD_INVALID","当前密码不正确");
   db.password(a.getName(),encoder.encode(p.newPassword()));
   for(var s:sessions.getAllSessions(a.getPrincipal(),false)) s.expireNow();
   logout(req);
 }
 private String normalize(String name) {
   String n=name==null?"":name.trim().toLowerCase(Locale.ROOT);
   if(!n.matches("[a-z0-9_]{3,32}")) throw ApiError.bad("用户名须为 3–32 位字母、数字或下划线");return n;
 }
 private void validatePassword(String p,String confirm) {
   if(p==null||p.length()<8||p.length()>64||p.getBytes(StandardCharsets.UTF_8).length>72) throw ApiError.bad("密码须为 8–64 位，且 UTF-8 长度不超过 72 字节");
   if(!p.equals(confirm)) throw ApiError.bad("两次密码不一致");
 }
 private Object publicUser(Models.User u) { return Map.of("id",u.id(),"username",u.username(),"createdAt",u.createdAt()); }
}
