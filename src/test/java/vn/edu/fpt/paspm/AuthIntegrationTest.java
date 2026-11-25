package vn.edu.fpt.paspm;

import java.time.Instant;
import java.sql.Timestamp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.security.StoreScope;
import vn.edu.fpt.paspm.user.AvatarObjectStorage;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:paspm;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
 "spring.datasource.username=sa","spring.datasource.password=",
 "spring.flyway.enabled=true","spring.sql.init.mode=never",
 "paspm.jwt-secret=test-only-signing-key-with-at-least-thirty-two-bytes",
 "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
 "paspm.avatar.directory=${java.io.tmpdir}/paspm-avatar-integration-${random.uuid}",
 "paspm.bootstrap-email=","paspm.bootstrap-password="})
@AutoConfigureMockMvc @Import(AuthIntegrationTest.StoreProbe.class)
class AuthIntegrationTest {
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired JdbcTemplate jdbc;@Autowired AuthService auth;@Autowired ObjectMapper json;
 @Autowired PasswordEncoder passwords;@Autowired JwtEncoder jwtEncoder;
 @Autowired JwtDecoder jwtDecoder;
 @MockitoBean JavaMailSender mail;
 @Autowired AvatarObjectStorage avatarStorage;
 final String password="TestingPassword123!";
 @BeforeEach void resetData(){jdbc.update("DELETE FROM external_identity");jdbc.update("DELETE FROM verification_challenge");jdbc.update("DELETE FROM app_user");jdbc.update("DELETE FROM store");reset(mail);}
 User active(String email,String role,Integer store){User u=users.createPendingUser("Test",email,role,store,passwords.encode(password),null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?",Timestamp.from(Instant.now()),u.id());return users.findById(u.id());}
 MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder r,Object body)throws Exception{return r.contentType("application/json").content(json.writeValueAsString(body));}
 String token(User u){return auth.token(u).accessToken();}
 static final String REFRESH_COOKIE="__Host-PASPM-REFRESH";
 static final String CSRF_COOKIE="__Host-PASPM-CSRF";
 static final String CSRF_HEADER="X-CSRF-Token";
 record TestSession(String accessToken,String refreshToken,String csrfToken){}
 TestSession loginCredentials(User u)throws Exception{
  var response=mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password",password)))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
   .andReturn();
  var payload=json.readTree(response.getResponse().getContentAsString());
  var refresh=response.getResponse().getCookie(REFRESH_COOKIE);
  var csrf=response.getResponse().getCookie(CSRF_COOKIE);
  assertNotNull(refresh);
  assertNotNull(csrf);
  assertFalse(payload.has("refreshToken"));
  var setCookies=response.getResponse().getHeaders("Set-Cookie");
  assertTrue(setCookies.stream().anyMatch(value->value.startsWith(REFRESH_COOKIE+"=")
   &&value.contains("Secure")&&value.contains("HttpOnly")&&value.contains("SameSite=Strict")&&value.contains("Path=/")));
  assertTrue(setCookies.stream().anyMatch(value->value.startsWith(CSRF_COOKIE+"=")
   &&value.contains("Secure")&&!value.contains("HttpOnly")&&value.contains("SameSite=Strict")));
  return new TestSession(payload.get("accessToken").asText(),refresh.getValue(),payload.get("csrfToken").asText());
 }
 TestSession refreshedSession(MvcResult response)throws Exception{
  var payload=json.readTree(response.getResponse().getContentAsString());
  return new TestSession(
   payload.get("accessToken").asText(),
   response.getResponse().getCookie(REFRESH_COOKIE).getValue(),
   payload.get("csrfToken").asText());
 }
 MockHttpServletRequestBuilder refreshRequest(TestSession session,String remoteAddress){
  return post("/api/auth/refresh")
   .cookie(new jakarta.servlet.http.Cookie(REFRESH_COOKIE,session.refreshToken()),
    new jakarta.servlet.http.Cookie(CSRF_COOKIE,session.csrfToken()))
   .header(CSRF_HEADER,session.csrfToken())
   .with(request->{request.setRemoteAddr(remoteAddress);return request;});
 }
 String latestOtp(){var c=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail,atLeastOnce()).send(c.capture());var match=Pattern.compile("code is ([0-9]{6})").matcher(c.getValue().getText());assertTrue(match.find());return match.group(1);}

 @Test void loginMeAndAnonymousAccess()throws Exception{
  User u=active("login@example.com","SYSTEM_ADMIN",null);
  var credentials=loginCredentials(u);
  String t=credentials.accessToken();
  assertEquals(43,credentials.refreshToken().length());
  assertEquals(3600,java.time.Duration.between(
   jwtDecoder.decode(t).getIssuedAt(),jwtDecoder.decode(t).getExpiresAt()).getSeconds());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password",password)))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.roleCode").value("SYSTEM_ADMIN")).andExpect(jsonPath("$.passwordHash").doesNotExist());
  mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password","wrong"))).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
 }

 @Test void refreshRotatesOpaqueTokenAndReplayRevokesItsFamily()throws Exception{
  User u=active("refresh-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  var credentials=loginCredentials(u);
  String original=credentials.refreshToken();
  Instant absoluteExpiry=Instant.now().plusSeconds(600);
  jdbc.update("UPDATE auth_refresh_session SET absolute_expires_at=?,idle_expires_at=? WHERE user_id=?",
   Timestamp.from(absoluteExpiry),Timestamp.from(absoluteExpiry.plusSeconds(600)),u.id());
  assertEquals(32,jdbc.queryForObject("SELECT OCTET_LENGTH(token_hash) FROM auth_refresh_token",Integer.class));
  assertFalse(java.util.Arrays.equals(original.getBytes(java.nio.charset.StandardCharsets.UTF_8),
   jdbc.queryForObject("SELECT token_hash FROM auth_refresh_token",byte[].class)));

  var refreshed=mvc.perform(refreshRequest(credentials,"refresh-rotate"))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
   .andExpect(jsonPath("$.expiresIn").value(3600)).andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
  TestSession next=refreshedSession(refreshed);
  String replacement=next.refreshToken();
  assertEquals(1,jdbc.queryForObject(
   "SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? AND idle_expires_at=absolute_expires_at",
   Integer.class,u.id()));

  mvc.perform(refreshRequest(credentials,"refresh-replay"))
   .andExpect(status().isUnauthorized())
   .andExpect(jsonPath("$.error").value("Invalid or expired refresh token"))
   .andExpect(jsonPath("$.refreshToken").doesNotExist());
  mvc.perform(refreshRequest(next,"refresh-family"))
   .andExpect(status().isUnauthorized());
 }

 @Test void refreshRejectsInvalidExpiredInactiveAndStaleVersionSessions()throws Exception{
  mvc.perform(post("/api/auth/refresh")
      .cookie(new jakarta.servlet.http.Cookie(CSRF_COOKIE,"csrf-cookie"))
      .header(CSRF_HEADER,"different-header"))
   .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("CSRF validation failed"));
  TestSession invalid=new TestSession("","not-a-refresh-token","csrf-invalid");
  mvc.perform(refreshRequest(invalid,"refresh-invalid"))
   .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Invalid or expired refresh token"));

  for(String expiryColumn:List.of("idle_expires_at","absolute_expires_at")){
   User u=active("r-expiry-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
   TestSession session=loginCredentials(u);
   jdbc.update("UPDATE auth_refresh_session SET "+expiryColumn+"=? WHERE user_id=?",
    Timestamp.from(Instant.now().minusSeconds(1)),u.id());
   mvc.perform(refreshRequest(session,"refresh-expired-"+expiryColumn))
    .andExpect(status().isUnauthorized());
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? AND revoked_at IS NOT NULL",Integer.class,u.id()));
  }

  User inactive=active("refresh-inactive-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  TestSession inactiveSession=loginCredentials(inactive);
  jdbc.update("UPDATE app_user SET status='INACTIVE' WHERE user_id=?",inactive.id());
  mvc.perform(refreshRequest(inactiveSession,"refresh-inactive"))
   .andExpect(status().isUnauthorized());

  User stale=active("refresh-stale-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  TestSession staleSession=loginCredentials(stale);
  jdbc.update("UPDATE app_user SET token_version=token_version+1 WHERE user_id=?",stale.id());
  mvc.perform(refreshRequest(staleSession,"refresh-stale"))
   .andExpect(status().isUnauthorized());
 }

 @Test void concurrentRefreshRequestsCannotBothSucceed()throws Exception{
  User u=active("refresh-race-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  TestSession session=loginCredentials(u);
  var ready=new CountDownLatch(2);
  var start=new CountDownLatch(1);
  var pool=Executors.newFixedThreadPool(2);
  try{
   java.util.concurrent.Callable<Integer> call=()->{
    ready.countDown();
    if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Refresh race did not start");
    return mvc.perform(refreshRequest(session,"refresh-race"))
     .andReturn().getResponse().getStatus();
   };
   var first=pool.submit(call);
   var second=pool.submit(call);
   assertTrue(ready.await(10,TimeUnit.SECONDS));
   start.countDown();
   List<Integer> outcomes=new ArrayList<>(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)));
   assertEquals(1,outcomes.stream().filter(code->code==200).count());
   assertEquals(1,outcomes.stream().filter(code->code==401).count());
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? AND revoked_at IS NOT NULL",Integer.class,u.id()));
  }finally{
   pool.shutdownNow();
  }
 }

 @Test void logoutRevokesOneDeviceAndLogoutAllRevokesEveryDeviceAndAccessTokens()throws Exception{
  User u=active("refresh-logout-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  TestSession first=loginCredentials(u);
  TestSession second=loginCredentials(u);
  mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+first.accessToken())
    .cookie(new jakarta.servlet.http.Cookie(CSRF_COOKIE,first.csrfToken())).header(CSRF_HEADER,"incorrect"))
   .andExpect(status().isForbidden());
  mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+first.accessToken())
    .cookie(new jakarta.servlet.http.Cookie(CSRF_COOKIE,first.csrfToken())).header(CSRF_HEADER,first.csrfToken()))
   .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Logged out"));
  mvc.perform(refreshRequest(first,"logout-one"))
   .andExpect(status().isUnauthorized());
  var secondRefresh=mvc.perform(refreshRequest(second,"logout-other"))
   .andExpect(status().isOk()).andReturn();
  TestSession rotated=refreshedSession(secondRefresh);

  mvc.perform(post("/api/auth/logout-all").header("Authorization","Bearer "+second.accessToken())
    .cookie(new jakarta.servlet.http.Cookie(CSRF_COOKIE,second.csrfToken())).header(CSRF_HEADER,second.csrfToken()))
   .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Logged out from all sessions"));
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+second.accessToken()))
   .andExpect(status().isUnauthorized());
  mvc.perform(refreshRequest(rotated,"logout-all"))
   .andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
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
 @Test void passwordChangeRequiresAuthenticationAndRevokesCurrentToken()throws Exception{
  User u=active("change-password-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  var credentials=loginCredentials(u);
  String oldToken=credentials.accessToken();
  mvc.perform(body(post("/api/auth/password/change"),Map.of("currentPassword",password,"newPassword","AnotherPassword456!")))
   .andExpect(status().isUnauthorized());

  mvc.perform(body(post("/api/auth/password/change").header("Authorization","Bearer "+oldToken),
     Map.of("currentPassword",password,"newPassword","AnotherPassword456!")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Password changed"));

  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+oldToken)).andExpect(status().isUnauthorized());
  mvc.perform(refreshRequest(credentials,"password-change"))
   .andExpect(status().isUnauthorized());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password",password))).andExpect(status().isUnauthorized());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password","AnotherPassword456!"))).andExpect(status().isOk());
 }
 @Test void passwordChangeRejectsWrongCurrentPasswordAndSamePassword()throws Exception{
  User u=active("change-password-verify-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  String userToken=token(u);
  mvc.perform(body(post("/api/auth/password/change").header("Authorization","Bearer "+userToken),
     Map.of("currentPassword","WrongCurrentPassword123!","newPassword","AnotherPassword456!")))
   .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Current password is incorrect"));
  mvc.perform(body(post("/api/auth/password/change").header("Authorization","Bearer "+userToken),
     Map.of("currentPassword",password,"newPassword",password)))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("New password must differ from current password"));
  mvc.perform(body(post("/api/auth/password/change").header("Authorization","Bearer "+userToken),
     Map.of("currentPassword",password,"newPassword","short")))
   .andExpect(status().isBadRequest());
  mvc.perform(body(post("/api/auth/password/change").header("Authorization","Bearer "+userToken),
     Map.of("currentPassword",password,"newPassword","Aa1"+"é".repeat(36))))
   .andExpect(status().isBadRequest());
  mvc.perform(body(post("/api/auth/password/change").header("Authorization","Bearer "+userToken),
     Map.of("currentPassword","é".repeat(40),"newPassword","AnotherPassword456!")))
   .andExpect(status().isBadRequest());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password",password))).andExpect(status().isOk());
 }
 @Test void passwordChangeRejectsInactiveAndPasswordlessAccounts()throws Exception{
  User inactive=active("change-password-inactive-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  String inactiveToken=token(inactive);
  jdbc.update("UPDATE app_user SET status='INACTIVE' WHERE user_id=?",inactive.id());
  mvc.perform(body(post("/api/auth/password/change").header("Authorization","Bearer "+inactiveToken),
     Map.of("currentPassword",password,"newPassword","AnotherPassword456!")))
   .andExpect(status().isUnauthorized());

  User passwordless=users.createPendingUser("Google only","change-password-google-"+java.util.UUID.randomUUID()+"@example.com",
     "END_CUSTOMER",null,null,null);
  jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?",
     Timestamp.from(Instant.now()),passwordless.id());
  String passwordlessToken=token(users.findById(passwordless.id()));
  mvc.perform(body(post("/api/auth/password/change").header("Authorization","Bearer "+passwordlessToken),
     Map.of("currentPassword",password,"newPassword","AnotherPassword456!")))
   .andExpect(status().isBadRequest())
   .andExpect(jsonPath("$.error").value("Password change is unavailable for this account"));
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
  mvc.perform(body(post("/api/auth/register"),Map.of("email","customer@example.com")))
   .andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value("Verification email sent")).andExpect(jsonPath("$.otp").doesNotExist());
  mvc.perform(body(post("/api/auth/register"),Map.of("email","customer@example.com")))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Email already exists"));
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
 @Test void passwordResetIsGenericSingleUseAndRevokesExistingTokens()throws Exception{
  User u=active("reset-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  var credentials=loginCredentials(u);
  String oldToken=credentials.accessToken();
  var known=mvc.perform(body(post("/api/auth/password-reset/request"),Map.of("email",u.email())))
   .andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value("If the account is eligible, a verification email will be sent"))
   .andExpect(jsonPath("$.otp").doesNotExist()).andReturn().getResponse().getContentAsString();
  var unknown=mvc.perform(body(post("/api/auth/password-reset/request"),Map.of("email","unknown-"+java.util.UUID.randomUUID()+"@example.com")))
   .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
  assertEquals(known,unknown);
  assertEquals("PASSWORD_RESET",jdbc.queryForObject("SELECT purpose FROM verification_challenge WHERE user_id=?",String.class,u.id()));
  String code=latestOtp(),wrong=code.equals("000000")?"111111":"000000";
  mvc.perform(body(post("/api/auth/password-reset/confirm"),Map.of("email",u.email(),"otp",wrong,"password","AnotherPassword456!")))
   .andExpect(status().isBadRequest());
  mvc.perform(body(post("/api/auth/password-reset/confirm"),Map.of("email",u.email(),"otp",code,"password","AnotherPassword456!")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Password reset complete"));
  mvc.perform(body(post("/api/auth/password-reset/confirm"),Map.of("email",u.email(),"otp",code,"password","AnotherPassword456!")))
   .andExpect(status().isBadRequest());
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+oldToken)).andExpect(status().isUnauthorized());
  mvc.perform(refreshRequest(credentials,"password-reset"))
   .andExpect(status().isUnauthorized());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password",password))).andExpect(status().isUnauthorized());
  mvc.perform(body(post("/api/auth/login"),Map.of("email",u.email(),"password","AnotherPassword456!"))).andExpect(status().isOk());
 }
 @Test void passwordResetOtpExpiryAttemptsAndSendLimitsApply()throws Exception{
  String expiredEmail="reset-expired-"+java.util.UUID.randomUUID()+"@example.com";
  User expired=active(expiredEmail,"END_CUSTOMER",null);
  mvc.perform(body(post("/api/auth/password-reset/request"),Map.of("email",expiredEmail))).andExpect(status().isAccepted());
  String expiredCode=latestOtp();
  jdbc.update("UPDATE verification_challenge SET expires_at=? WHERE user_id=?",Timestamp.from(Instant.now().minusSeconds(1)),expired.id());
  mvc.perform(body(post("/api/auth/password-reset/confirm"),Map.of("email",expiredEmail,"otp",expiredCode,"password","AnotherPassword456!")))
   .andExpect(status().isBadRequest());

  String attemptsEmail="reset-attempts-"+java.util.UUID.randomUUID()+"@example.com";
  User attemptsUser=active(attemptsEmail,"END_CUSTOMER",null);
  mvc.perform(body(post("/api/auth/password-reset/request"),Map.of("email",attemptsEmail))).andExpect(status().isAccepted());
  String code=latestOtp(),wrong=code.equals("000000")?"111111":"000000";
  for(int i=0;i<5;i++)mvc.perform(body(post("/api/auth/password-reset/confirm"),Map.of("email",attemptsEmail,"otp",wrong,"password","AnotherPassword456!"))).andExpect(status().isBadRequest());
  assertEquals(5,jdbc.queryForObject("SELECT attempts FROM verification_challenge WHERE user_id=?",Integer.class,attemptsUser.id()));
  mvc.perform(body(post("/api/auth/password-reset/confirm"),Map.of("email",attemptsEmail,"otp",code,"password","AnotherPassword456!"))).andExpect(status().isBadRequest());

  User limited=active("reset-limit-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  for(int i=0;i<5;i++)mvc.perform(body(post("/api/auth/password-reset/request"),Map.of("email",limited.email()))).andExpect(status().isAccepted());
  mvc.perform(body(post("/api/auth/password-reset/request"),Map.of("email",limited.email()))).andExpect(status().isTooManyRequests());
 }
 @Test void passwordResetCooldownIsGenericAndAllowsRequestAfterExpiry()throws Exception{
  User u=active("reset-cooldown-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  mvc.perform(body(post("/api/auth/password-reset/request"),Map.of("email",u.email()))).andExpect(status().isAccepted());
  String firstHash=jdbc.queryForObject("SELECT code_hash FROM verification_challenge WHERE user_id=?",String.class,u.id());
  mvc.perform(body(post("/api/auth/password-reset/request"),Map.of("email",u.email()))).andExpect(status().isAccepted());
  assertEquals(firstHash,jdbc.queryForObject("SELECT code_hash FROM verification_challenge WHERE user_id=?",String.class,u.id()));
  jdbc.update("UPDATE verification_challenge SET issued_at=? WHERE user_id=?",Timestamp.from(Instant.now().minusSeconds(120)),u.id());
  mvc.perform(body(post("/api/auth/password-reset/request"),Map.of("email",u.email()))).andExpect(status().isAccepted());
  assertNotEquals(firstHash,jdbc.queryForObject("SELECT code_hash FROM verification_challenge WHERE user_id=?",String.class,u.id()));
 }
 @Test void profileAvatarIsSelfScopedAndValidatesImageContents()throws Exception{
  User u=active("avatar-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  User other=active("avatar-other-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  String userToken=token(u),otherToken=token(other);
  byte[] png;
  try(ByteArrayOutputStream output=new ByteArrayOutputStream()){
   assertTrue(ImageIO.write(new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB),"png",output));
   png=output.toByteArray();
  }

  mvc.perform(multipart("/api/users/me/avatar").file(new MockMultipartFile("file","empty.png","image/png",new byte[0]))
     .with(request -> {request.setMethod("PUT");return request;}).header("Authorization","Bearer "+userToken))
   .andExpect(status().isBadRequest());
  mvc.perform(multipart("/api/users/me/avatar").file(new MockMultipartFile("file","avatar.png","image/png",new byte[]{1,2,3}))
     .with(request -> {request.setMethod("PUT");return request;}).header("Authorization","Bearer "+userToken))
   .andExpect(status().isBadRequest());
  mvc.perform(multipart("/api/users/me/avatar").file(new MockMultipartFile("file","../../avatar.exe","text/plain",png))
     .with(request -> {request.setMethod("PUT");return request;}).header("Authorization","Bearer "+userToken))
   .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value("/api/users/"+u.id()+"/avatar"));

  User updated=users.findById(u.id());
  assertTrue(updated.avatarKey().matches("[0-9a-f-]{36}\\.png"));
  assertArrayEquals(png,avatarStorage.get(updated.avatarKey()).content());
  mvc.perform(get("/api/users/"+u.id()+"/avatar").header("Authorization","Bearer "+userToken))
   .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
  mvc.perform(get("/api/users/"+u.id()+"/avatar").header("Authorization","Bearer "+otherToken)).andExpect(status().isForbidden());
  mvc.perform(multipart("/api/users/me/avatar").file(new MockMultipartFile("file","large.png","image/png",new byte[5*1024*1024+1]))
     .with(request -> {request.setMethod("PUT");return request;}).header("Authorization","Bearer "+userToken))
   .andExpect(status().isPayloadTooLarge());
  mvc.perform(delete("/api/users/me/avatar").header("Authorization","Bearer "+userToken))
   .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").doesNotExist());
  assertNull(users.findById(u.id()).avatarKey());
  assertThrows(java.io.IOException.class,()->avatarStorage.get(updated.avatarKey()));
  mvc.perform(get("/api/users/"+u.id()+"/avatar").header("Authorization","Bearer "+userToken)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/users/me/avatar")).andExpect(status().isUnauthorized());
 }
 @Test void passwordResetMailFailureDoesNotDiscloseAccountAndRollsBackChallenge()throws Exception{
  User u=active("reset-mail-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  doThrow(new MailSendException("mail unavailable")).when(mail).send(any(SimpleMailMessage.class));
  mvc.perform(body(post("/api/auth/password-reset/request"),Map.of("email",u.email())))
   .andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value("If the account is eligible, a verification email will be sent"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM verification_challenge WHERE user_id=?",Integer.class,u.id()));
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

 @Test void statusAndRoleChangesRevokeRefreshSessions()throws Exception{
  User admin=active("refresh-admin-"+java.util.UUID.randomUUID()+"@example.com","SYSTEM_ADMIN",null);
  User inactive=active("refresh-status-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  TestSession inactiveSession=loginCredentials(inactive);
  mvc.perform(body(patch("/api/users/"+inactive.id()+"/status").header("Authorization","Bearer "+token(admin)),
     Map.of("status","INACTIVE"))).andExpect(status().isOk());
  mvc.perform(refreshRequest(inactiveSession,"refresh-status-change"))
   .andExpect(status().isUnauthorized());

  User roleChanged=active("refresh-role-"+java.util.UUID.randomUUID()+"@example.com","END_CUSTOMER",null);
  TestSession roleSession=loginCredentials(roleChanged);
  mvc.perform(body(put("/api/users/"+roleChanged.id()+"/role").header("Authorization","Bearer "+token(admin)),
     Map.of("roleCode","SYSTEM_ADMIN"))).andExpect(status().isOk());
  mvc.perform(refreshRequest(roleSession,"refresh-role-change"))
   .andExpect(status().isUnauthorized());
 }

 @Test void refreshEndpointRateLimitIsAppliedAndDoesNotEchoSecrets()throws Exception{
  TestSession invalid=new TestSession("","invalid-"+java.util.UUID.randomUUID(),"csrf-rate");
  for(int i=0;i<30;i++){
   mvc.perform(refreshRequest(invalid,"refresh-rate-limit"))
    .andExpect(status().isUnauthorized())
    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(invalid.refreshToken()))));
  }
  mvc.perform(refreshRequest(invalid,"refresh-rate-limit"))
   .andExpect(status().isTooManyRequests())
   .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(invalid.refreshToken()))));
 }
 @Test void swaggerContractAvailableAndPasswordsHashed()throws Exception{
  User a=active("swagger@example.com","SYSTEM_ADMIN",null);assertNotEquals(password,a.passwordHash());
  mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
   .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
   .andExpect(jsonPath("$.paths['/api/auth/password-reset/request'].post").exists())
   .andExpect(jsonPath("$.paths['/api/auth/password-reset/confirm'].post").exists())
   .andExpect(jsonPath("$.paths['/api/auth/password/change'].post").exists())
   .andExpect(jsonPath("$.paths['/api/auth/password/change'].post.requestBody.content['application/json'].schema['$ref']")
     .value("#/components/schemas/ChangePassword"))
   .andExpect(jsonPath("$.paths['/api/auth/refresh'].post").exists())
   .andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists())
   .andExpect(jsonPath("$.paths['/api/auth/logout-all'].post").exists())
   .andExpect(jsonPath("$.components.schemas.Token.properties.csrfToken.type").value("string"))
   .andExpect(jsonPath("$.components.schemas.Token.properties.refreshToken").doesNotExist())
   .andExpect(jsonPath("$.components.schemas.ChangePassword.properties.currentPassword.maxLength").value(72))
   .andExpect(jsonPath("$.components.schemas.ChangePassword.properties.newPassword.minLength").value(12))
   .andExpect(jsonPath("$.paths['/api/users/me/avatar'].put").exists())
   .andExpect(jsonPath("$.paths['/api/users/{id}/avatar'].get").exists());
 }
 @RestController static class StoreProbe {
  final StoreScope scope;StoreProbe(StoreScope s){scope=s;}
  @GetMapping("/test/store/{id}") @PreAuthorize("hasRole('STORE_ADMIN')")
  public Map<String,Integer> probe(@PathVariable int id,@AuthenticationPrincipal Jwt jwt){return Map.of("storeId",scope.require(jwt,id));}
 }
}
