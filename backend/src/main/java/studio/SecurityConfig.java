package studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.security.core.session.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.*;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.authentication.session.*;
import java.util.List;

@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(); }
 @Bean SessionRegistry sessions() { return new SessionRegistryImpl(); }
 @Bean static HttpSessionEventPublisher events() { return new HttpSessionEventPublisher(); }
 @Bean SecurityContextRepository contexts() { return new HttpSessionSecurityContextRepository(); }
 @Bean CsrfTokenRepository csrfTokens() { return new HttpSessionCsrfTokenRepository(); }
 @Bean SessionAuthenticationStrategy sessionStrategy(SessionRegistry sessions,CsrfTokenRepository csrf) {
   return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(csrf),new RegisterSessionAuthenticationStrategy(sessions)));
 }
 @Bean SecurityFilterChain security(HttpSecurity http,ObjectMapper json,SecurityContextRepository contexts,SessionRegistry sessions,CsrfTokenRepository csrf) throws Exception {
   return http.authorizeHttpRequests(a -> a
       .requestMatchers("/api/auth/csrf","/api/auth/register","/api/auth/login","/api/categories","/api/templates/**","/api/demo-files/**","/actuator/health","/error").permitAll()
       .anyRequest().authenticated())
     .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
     .securityContext(c -> c.securityContextRepository(contexts))
     .sessionManagement(s -> s.maximumSessions(-1).sessionRegistry(sessions).expiredSessionStrategy(event -> {
       var res=event.getResponse();res.setStatus(401);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getOutputStream(),ApiError.body("UNAUTHENTICATED","会话已失效，请重新登录"));
     }))
     .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> {
       res.setStatus(401);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getOutputStream(),ApiError.body("UNAUTHENTICATED","请先登录"));
     }).accessDeniedHandler((req,res,ex) -> {
       res.setStatus(403);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getOutputStream(),ApiError.body("CSRF_INVALID","安全令牌已失效，请刷新后重试"));
     })).logout(l -> l.disable()).requestCache(c -> c.disable()).build();
 }
}
