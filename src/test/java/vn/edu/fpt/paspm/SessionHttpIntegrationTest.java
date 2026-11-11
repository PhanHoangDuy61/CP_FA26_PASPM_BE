package vn.edu.fpt.paspm;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:session_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","paspm.jwt-secret=session-test-key-at-least-thirty-two-bytes","paspm.otp-secret=session-test-otp-at-least-thirty-two-bytes","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.session.cookie-secure=false"})
@AutoConfigureMockMvc
class SessionHttpIntegrationTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired PasswordEncoder passwords; @Autowired ObjectMapper json;
 String email; final String password="SessionTesting123!";
 @BeforeEach void user(){email="session-"+java.util.UUID.randomUUID()+"@example.invalid";db.update("INSERT INTO app_user(email,name,role_id,status,password_hash,email_verified_at) SELECT ?,?,role_id,'ACTIVE',?,CURRENT_TIMESTAMP FROM role WHERE code='END_CUSTOMER'",email,"Test",passwords.encode(password));}
 MvcResult login()throws Exception{return mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("email",email,"password",password)))).andExpect(status().isOk()).andExpect(jsonPath("$.csrfToken").isNotEmpty()).andReturn();}
 String field(MvcResult r,String key)throws Exception{return json.readTree(r.getResponse().getContentAsString()).get(key).asText();}
 Cookie refresh(MvcResult r){return r.getResponse().getCookie("PASPM-REFRESH");}
 Cookie csrf(MvcResult r){return r.getResponse().getCookie("PASPM-CSRF");}
 MvcResult rotate(MvcResult r)throws Exception{return mvc.perform(post("/api/auth/refresh").cookie(refresh(r),csrf(r)).header("X-CSRF-Token",field(r,"csrfToken"))).andExpect(status().isOk()).andReturn();}
 @Test void rotationAndReplayRevokeFamily()throws Exception{var a=login();assertTrue(refresh(a).isHttpOnly());assertFalse(csrf(a).isHttpOnly());var b=rotate(a);assertNotEquals(refresh(a).getValue(),refresh(b).getValue());mvc.perform(post("/api/auth/refresh").cookie(refresh(a),csrf(a)).header("X-CSRF-Token",field(a,"csrfToken"))).andExpect(status().isUnauthorized());mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+field(b,"accessToken"))).andExpect(status().isUnauthorized());}
 @Test void csrfAndOriginFailuresDoNotRotate()throws Exception{var a=login();mvc.perform(post("/api/auth/refresh").cookie(refresh(a),csrf(a))).andExpect(status().isForbidden());mvc.perform(post("/api/auth/refresh").cookie(refresh(a),csrf(a)).header("X-CSRF-Token",field(a,"csrfToken")).header("Origin","https://evil.invalid")).andExpect(status().isForbidden());rotate(a);}
 @Test void logoutOneAndLogoutAll()throws Exception{var a=login();var b=login();mvc.perform(post("/api/auth/logout").cookie(refresh(a),csrf(a)).header("Authorization","Bearer "+field(a,"accessToken")).header("X-CSRF-Token",field(a,"csrfToken"))).andExpect(status().isOk());mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+field(a,"accessToken"))).andExpect(status().isUnauthorized());var c=rotate(b);mvc.perform(post("/api/auth/logout-all").cookie(refresh(c),csrf(c)).header("Authorization","Bearer "+field(c,"accessToken")).header("X-CSRF-Token",field(c,"csrfToken"))).andExpect(status().isOk());mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+field(b,"accessToken"))).andExpect(status().isUnauthorized());}
 @Test void expiredAndChangedAccountCannotRefresh()throws Exception{var a=login();db.update("UPDATE auth_session SET expires_at=? WHERE user_id=(SELECT user_id FROM app_user WHERE email=?)",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(5)),email);mvc.perform(post("/api/auth/refresh").cookie(refresh(a),csrf(a)).header("X-CSRF-Token",field(a,"csrfToken"))).andExpect(status().isUnauthorized());var b=login();db.update("UPDATE app_user SET token_version=token_version+1 WHERE email=?",email);mvc.perform(post("/api/auth/refresh").cookie(refresh(b),csrf(b)).header("X-CSRF-Token",field(b,"csrfToken"))).andExpect(status().isUnauthorized());}

 @Test void cookieFlagsAndCacheControl() throws Exception {
   var a=login();
   assertEquals("/",refresh(a).getPath());
   assertEquals("Strict",refresh(a).getAttribute("SameSite"));
   assertFalse(refresh(a).getSecure());
   assertEquals("no-store",a.getResponse().getHeader("Cache-Control"));
   assertEquals(field(a,"csrfToken"),csrf(a).getValue());
 }
 @Test void csrfCookieAndHeaderMustBothMatch() throws Exception {
   var a=login();
   mvc.perform(post("/api/auth/refresh").cookie(refresh(a)).header("X-CSRF-Token",field(a,"csrfToken"))).andExpect(status().isForbidden());
   mvc.perform(post("/api/auth/refresh").cookie(refresh(a),new Cookie("PASPM-CSRF","bad")).header("X-CSRF-Token","bad")).andExpect(status().isForbidden());
   rotate(a);
 }
 @Test void logoutRequiresBearerAndMatchingSession() throws Exception {
   var a=login(); var b=login();
   mvc.perform(post("/api/auth/logout").cookie(refresh(a),csrf(a)).header("X-CSRF-Token",field(a,"csrfToken"))).andExpect(status().isUnauthorized());
   mvc.perform(post("/api/auth/logout").cookie(refresh(a),csrf(a)).header("Authorization","Bearer "+field(b,"accessToken")).header("X-CSRF-Token",field(a,"csrfToken"))).andExpect(status().isUnauthorized());
   rotate(a); rotate(b);
 }
 @Test void hostileLoginOriginCannotCreateSession() throws Exception {
   mvc.perform(post("/api/auth/login").header("Origin","https://evil.invalid").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("email",email,"password",password)))).andExpect(status().isForbidden());
   assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM auth_session s JOIN app_user u ON u.user_id=s.user_id WHERE u.email=?",Integer.class,email));
 }
 @Test void accountDeactivationPreventsRefresh() throws Exception {
   var a=login(); db.update("UPDATE app_user SET status='INACTIVE' WHERE email=?",email);
   mvc.perform(post("/api/auth/refresh").cookie(refresh(a),csrf(a)).header("X-CSRF-Token",field(a,"csrfToken"))).andExpect(status().isUnauthorized());
 }
 @Test void concurrentReuseHasOneWinnerAndRevokesFamily() throws Exception {
   var a=login();
   var gate=new java.util.concurrent.CountDownLatch(1);
   var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
   java.util.concurrent.Callable<MvcResult> call=()->{gate.await();return mvc.perform(post("/api/auth/refresh").cookie(refresh(a),csrf(a)).header("X-CSRF-Token",field(a,"csrfToken"))).andReturn();};
   try {
     var one=pool.submit(call);var two=pool.submit(call);gate.countDown();
     var x=one.get(10,java.util.concurrent.TimeUnit.SECONDS);var y=two.get(10,java.util.concurrent.TimeUnit.SECONDS);
     assertEquals(java.util.Set.of(200,401),java.util.Set.of(x.getResponse().getStatus(),y.getResponse().getStatus()));
     var winner=x.getResponse().getStatus()==200?x:y;
     mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+field(winner,"accessToken"))).andExpect(status().isUnauthorized());
   } finally {pool.shutdownNow();}
 }
 @Test void openApiDoesNotRequireBearerForSessionEntryPoints() throws Exception {
   var result=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
   var document=json.readTree(result.getResponse().getContentAsString());
   for (String path:java.util.List.of("login","google","refresh")) {
     var security=document.path("paths").path("/api/auth/"+path).path("post").path("security");
     assertTrue(security.isArray() && security.isEmpty(),path+" must explicitly override global bearer security");
   }
 }
}
