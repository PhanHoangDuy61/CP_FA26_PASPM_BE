package vn.edu.fpt.paspm;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.security.StoreScope;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:paspm;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
 "spring.datasource.username=sa","spring.datasource.password=",
 "spring.flyway.enabled=false","spring.sql.init.mode=always",
 "spring.sql.init.schema-locations=classpath:db/migration/V1__shared_store_and_f01.sql,classpath:db/migration/V2__allow_name_after_email_verification.sql",
 "paspm.jwt-secret=test-only-signing-key-with-at-least-thirty-two-bytes",
 "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
 "paspm.bootstrap-email=","paspm.bootstrap-password="})
@AutoConfigureMockMvc @Import(AuthIntegrationTest.StoreProbe.class)
class AuthIntegrationTest {
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired JdbcTemplate jdbc;@Autowired AuthService auth;@Autowired ObjectMapper json;
 @Autowired PasswordEncoder passwords;@Autowired JwtEncoder jwtEncoder;
 @MockitoBean JavaMailSender mail;
 final String password="TestingPassword123!";
 @BeforeEach void resetData(){jdbc.update("DELETE FROM external_identity");jdbc.update("DELETE FROM verification_challenge");jdbc.update("DELETE FROM app_user");jdbc.update("DELETE FROM store");reset(mail);}
 User active(String email,String role,Integer store){User u=users.createPendingUser("Test",email,role,store,passwords.encode(password),null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?",Timestamp.from(Instant.now()),u.id());return users.findById(u.id());}
 MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder r,Object body)throws Exception{return r.contentType("application/json").content(json.writeValueAsString(body));}
 String token(User u){return auth.token(u).accessToken();}
 String latestOtp(){var c=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail,atLeastOnce()).send(c.capture());var match=Pattern.compile("code is ([0-9]{6})").matcher(c.getValue().getText());assertTrue(match.find());return match.group(1);}

 @Test void loginMeAndAnonymousAccess()throws Exception{
  User u=active("login@example.com","SYSTEM_ADMIN",null);
  var res=mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password",password))).andExpect(status().isOk()).andReturn();
  String t=json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.roleCode").value("SYSTEM_ADMIN")).andExpect(jsonPath("$.passwordHash").doesNotExist());
  mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password","wrong"))).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
 }
 @Test void loginRejectsUnavailableAccountsAndInvalidFields()throws Exception{
  User pending=users.createPendingUser("Pending","login-pending@example.com","END_CUSTOMER",null,passwords.encode(password),"SELF_REGISTER");
  User inactive=active("login-inactive@example.com","END_CUSTOMER",null);
  jdbc.update("UPDATE app_user SET status='INACTIVE' WHERE user_id=?",inactive.id());
  User unverified=active("login-unverified@example.com","END_CUSTOMER",null);
  jdbc.update("UPDATE app_user SET email_verified_at=NULL WHERE user_id=?",unverified.id());
  for(String email:new String[]{pending.email(),inactive.email(),unverified.email(),"login-unknown@example.com"}){
   mvc.perform(body(post("/api/auth/login"),Map.of("email",email,"password",password)))
    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Invalid credentials or account unavailable"));
  }
  mvc.perform(body(post("/api/auth/login"),Map.of("email","invalid-email","password",password))).andExpect(status().isBadRequest());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",pending.email()))).andExpect(status().isBadRequest());
 }
 @Test void currentUserUsesTokenIdentityAndRejectsMissingOrExpiredToken()throws Exception{
  jdbc.update("INSERT INTO store(name) VALUES('Current User Store')");
  int store=jdbc.queryForObject("SELECT store_id FROM store",Integer.class);
  User u=active("me-store@example.com","STORE_ADMIN",store);
  mvc.perform(get("/api/auth/me").param("userId","99999").header("Authorization","Bearer "+token(u)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(u.id()))
   .andExpect(jsonPath("$.email").value(u.email())).andExpect(jsonPath("$.name").value(u.name()))
   .andExpect(jsonPath("$.roleCode").value(u.role())).andExpect(jsonPath("$.storeId").value(store))
   .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.emailVerified").value(true))
   .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.otp").doesNotExist());
  mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  Instant now=Instant.now();
  var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("paspm").subject(String.valueOf(u.id()))
   .issuedAt(now.minusSeconds(600)).expiresAt(now.minusSeconds(120)).claim("ver",u.version()).build();
  var header=org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
  String expired=jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header,claims)).getTokenValue();
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
 }
 @Test void registrationCannotChooseRoleAndOtpIsSingleUse()throws Exception{
  mvc.perform(body(post("/api/auth/register"),Map.of("email","customer@example.com","roleCode","SYSTEM_ADMIN"))).andExpect(status().isBadRequest());
  mvc.perform(body(post("/api/auth/register"),Map.of("email","customer@example.com"))).andExpect(status().isAccepted()).andExpect(jsonPath("$.otp").doesNotExist());
  User u=users.findByEmail("customer@example.com").orElseThrow();assertEquals("END_CUSTOMER",u.role());assertEquals("PENDING_VERIFICATION",u.status());assertNull(u.name());assertNull(u.passwordHash());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password",password))).andExpect(status().isUnauthorized());
  mvc.perform(body(post("/api/auth/register/complete"),Map.of("email",u.email(),"name","Customer","password",password))).andExpect(status().isBadRequest());
  String code=latestOtp();
  mvc.perform(body(post("/api/auth/otp/verify"),Map.of("email",u.email(),"otp",code))).andExpect(status().isOk());
  mvc.perform(body(post("/api/auth/otp/verify"),Map.of("email",u.email(),"otp",code))).andExpect(status().isBadRequest());
  User verified=users.findById(u.id());assertFalse(verified.active());assertNotNull(verified.verifiedAt());
  mvc.perform(body(post("/api/auth/register/complete"),Map.of("email",u.email(),"name","Customer","password",password))).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Registration complete"));
  assertTrue(users.findById(u.id()).active());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password",password))).andExpect(status().isOk());
  mvc.perform(body(post("/api/auth/register/complete"),Map.of("email",u.email(),"name","Customer","password",password))).andExpect(status().isBadRequest());
 }
 @Test void adminInviteActivatesWithInitialPassword()throws Exception{
  User admin=active("admin@example.com","SYSTEM_ADMIN",null);String t=token(admin);
  mvc.perform(body(post("/api/users"),Map.of("name","Employee","email","invite@example.com","roleCode","SYSTEM_ADMIN"))).andExpect(status().isUnauthorized());
  mvc.perform(body(post("/api/users").header("Authorization","Bearer "+t),Map.of("name","Employee","email","invite@example.com","roleCode","SYSTEM_ADMIN"))).andExpect(status().isCreated());
  String code=latestOtp();
  mvc.perform(body(post("/api/auth/otp/verify"),Map.of("email","invite@example.com","otp",code))).andExpect(status().isBadRequest());
  mvc.perform(body(post("/api/auth/activate"),Map.of("email","invite@example.com","otp",code,"password",password))).andExpect(status().isOk());
  mvc.perform(body(post("/api/auth/login"),Map.of("email","invite@example.com","password",password))).andExpect(status().isOk());
 }
 @Test void invalidOtpAttemptsPersistAndLockChallenge()throws Exception{
  mvc.perform(body(post("/api/auth/register"),Map.of("email","attempts@example.com"))).andExpect(status().isAccepted());
  String code=latestOtp(),wrong=code.equals("000000")?"111111":"000000";
  for(int i=0;i<5;i++)mvc.perform(body(post("/api/auth/otp/verify"),Map.of("email","attempts@example.com","otp",wrong))).andExpect(status().isBadRequest());
  assertEquals(5,jdbc.queryForObject("SELECT attempts FROM verification_challenge",Integer.class));
  mvc.perform(body(post("/api/auth/otp/verify"),Map.of("email","attempts@example.com","otp",code))).andExpect(status().isBadRequest());
 }
 @Test void resendRevokesOldChallengeAndCooldownApplies()throws Exception{
  mvc.perform(body(post("/api/auth/register"),Map.of("email","resend@example.com"))).andExpect(status().isAccepted());
  String oldHash=jdbc.queryForObject("SELECT code_hash FROM verification_challenge",String.class);
  mvc.perform(body(post("/api/auth/otp/resend"),Map.of("email","resend@example.com","purpose","SELF_REGISTER"))).andExpect(status().isTooManyRequests());
  jdbc.update("UPDATE verification_challenge SET issued_at=?",Timestamp.from(Instant.now().minusSeconds(120)));
  mvc.perform(body(post("/api/auth/otp/resend"),Map.of("email","resend@example.com","purpose","SELF_REGISTER"))).andExpect(status().isAccepted());
  assertNotEquals(oldHash,jdbc.queryForObject("SELECT code_hash FROM verification_challenge",String.class));
 }
 @Test void expiredOtpAndManualActivationAreDenied()throws Exception{
  User admin=active("expireadmin@example.com","SYSTEM_ADMIN",null);
  mvc.perform(body(post("/api/auth/register"),Map.of("email","expired@example.com"))).andExpect(status().isAccepted());String code=latestOtp();
  User u=users.findByEmail("expired@example.com").orElseThrow();
  jdbc.update("UPDATE verification_challenge SET expires_at=?",Timestamp.from(Instant.now().minusSeconds(60)));
  mvc.perform(body(post("/api/auth/otp/verify"),Map.of("email",u.email(),"otp",code))).andExpect(status().isBadRequest());
  mvc.perform(body(patch("/api/users/"+u.id()+"/status").header("Authorization","Bearer "+token(admin)),Map.of("status","ACTIVE"))).andExpect(status().isBadRequest());
 }
 @Test void roleChangeRevokesExistingToken()throws Exception{
  User a=active("roleadmin@example.com","SYSTEM_ADMIN",null),b=active("target@example.com","SYSTEM_ADMIN",null);String old=token(b);
  mvc.perform(body(put("/api/users/"+b.id()+"/role").header("Authorization","Bearer "+token(a)),Map.of("roleCode","END_CUSTOMER"))).andExpect(status().isOk());
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/users").header("Authorization","Bearer "+token(users.findById(b.id())))).andExpect(status().isForbidden());
 }
 @Test void disablingUserRevokesToken()throws Exception{
  User a=active("disableadmin@example.com","SYSTEM_ADMIN",null),b=active("disabletarget@example.com","END_CUSTOMER",null);String old=token(b);
  mvc.perform(body(patch("/api/users/"+b.id()+"/status").header("Authorization","Bearer "+token(a)),Map.of("status","INACTIVE"))).andExpect(status().isOk());
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
 }
 @Test void storeBoundaryIsEnforced()throws Exception{
  jdbc.update("INSERT INTO store(name) VALUES('A'),('B')");var ids=jdbc.queryForList("SELECT store_id FROM store ORDER BY store_id",Integer.class);
  User u=active("store@example.com","STORE_ADMIN",ids.get(0));String t=token(u);
  mvc.perform(get("/test/store/"+ids.get(0)).header("Authorization","Bearer "+t)).andExpect(status().isOk());
  mvc.perform(get("/test/store/"+ids.get(1)).header("Authorization","Bearer "+t)).andExpect(status().isForbidden());
  mvc.perform(get("/api/users").header("Authorization","Bearer "+t)).andExpect(status().isForbidden());
 }
 @Test void smtpFailureRollsBackRegistration()throws Exception{
  doThrow(new MailSendException("test unavailable")).when(mail).send(any(SimpleMailMessage.class));
  mvc.perform(body(post("/api/auth/register"),Map.of("email","mailfail@example.com"))).andExpect(status().isServiceUnavailable());
  assertTrue(users.findByEmail("mailfail@example.com").isEmpty());
 }
 @Test void googleFailsClosedWithoutClientConfiguration()throws Exception{
  mvc.perform(body(post("/api/auth/google"),Map.of("idToken","invalid"))).andExpect(status().isServiceUnavailable());
  mvc.perform(body(post("/api/auth/google/register"),Map.of("idToken","invalid"))).andExpect(status().isServiceUnavailable());
  mvc.perform(body(post("/api/auth/google/link"),Map.of("idToken","invalid"))).andExpect(status().isUnauthorized());
 }
 @Test void swaggerContractAvailableAndPasswordsHashed()throws Exception{
  User a=active("swagger@example.com","SYSTEM_ADMIN",null);assertNotEquals(password,a.passwordHash());
  mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
 }
 @RestController static class StoreProbe {
  final StoreScope scope;StoreProbe(StoreScope s){scope=s;}
  @GetMapping("/test/store/{id}") @PreAuthorize("hasRole('STORE_ADMIN')")
  public Map<String,Integer> probe(@PathVariable int id,@AuthenticationPrincipal Jwt jwt){return Map.of("storeId",scope.require(jwt,id));}
 }
}
