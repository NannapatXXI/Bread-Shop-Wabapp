package com.breadShop.XXI.integration;

import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.Mailservice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Test: Auth API (register / login)
 *
 * @MockitoBean Mailservice — แทนที่ mail bean ด้วย mock
 *   ทำไม? ถ้าไม่ mock การสมัครสมาชิกจะส่ง email จริงออกไปทุกครั้งที่รัน test
 *   mock ทำให้ทดสอบ logic ได้โดยไม่ต้องการ SMTP server
 *
 * @Transactional — rollback ทุกอย่างหลัง test แต่ละตัว
 *   ไม่ต้องเขียน @AfterEach ลบข้อมูล เพราะ transaction จัดการให้เอง
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MySqlContainerConfig.class)
@EnabledIfSystemProperty(named = "integration", matches = "true")
@DisplayName("Auth API Integration Tests")
class AuthApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // mock mail service — ป้องกันส่ง email จริงตอนรัน test
    @MockitoBean
    private Mailservice mailService;

    // ─────────────────────────────────────────────────────────────────
    //  POST /api/v1/auth/register
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register → บันทึก user ลง DB จริง และคืน success")
    void register_shouldPersistUserAndReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "it_user",
                              "email": "it_user@breadshop.test",
                              "password": "pass1234"
                            }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // ตรวจว่า user บันทึกลง MySQL จริง — นี่คือสิ่งที่ unit test ทำไม่ได้
        assertThat(userRepository.existsByEmail("it_user@breadshop.test")).isTrue();
    }

    @Test
    @DisplayName("register → password ถูก hash ด้วย BCrypt ก่อนบันทึก")
    void register_shouldHashPasswordBeforeSaving() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "it_user",
                              "email": "it_user@breadshop.test",
                              "password": "myPlainPassword"
                            }
                        """))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail("it_user@breadshop.test").orElseThrow();
        // password ในDB ต้องไม่ใช่ plaintext
        assertThat(saved.getPassword()).isNotEqualTo("myPlainPassword");
        // แต่ match กับ plaintext ผ่าน BCrypt
        assertThat(passwordEncoder.matches("myPlainPassword", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("register ซ้ำ email เดิม → 4xx error")
    void register_withDuplicateEmail_shouldReturn4xx() throws Exception {
        // insert user ก่อน
        userRepository.save(new User("existing_user", "it_user@breadshop.test",
                passwordEncoder.encode("pass1234")));

        // สมัครซ้ำด้วย email เดิม
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "new_name",
                              "email": "it_user@breadshop.test",
                              "password": "pass1234"
                            }
                        """))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─────────────────────────────────────────────────────────────────
    //  POST /api/v1/auth/login
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login credentials ถูก → set httpOnly cookie access_token + refresh_token")
    void login_withValidCredentials_shouldSetHttpOnlyCookies() throws Exception {
        // insert user ที่มี hashed password
        userRepository.save(new User("it_user", "it_user@breadshop.test",
                passwordEncoder.encode("pass1234")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "usernameOrEmail": "it_user@breadshop.test",
                              "password": "pass1234"
                            }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // ตรวจว่า cookie ถูก set จริง
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                // ตรวจว่าเป็น httpOnly (ป้องกัน XSS ขโมย token)
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    @DisplayName("login password ผิด → 4xx")
    void login_withWrongPassword_shouldReturn4xx() throws Exception {
        userRepository.save(new User("it_user", "it_user@breadshop.test",
                passwordEncoder.encode("correct_pass")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "usernameOrEmail": "it_user@breadshop.test",
                              "password": "wrong_pass"
                            }
                        """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("login email ไม่มีในระบบ → 4xx")
    void login_withNonExistentEmail_shouldReturn4xx() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "usernameOrEmail": "ghost@breadshop.test",
                              "password": "anypass"
                            }
                        """))
                .andExpect(status().is4xxClientError());
    }

    // ───────────── บัญชีที่ถูกแบน ─────────────

    private String loginBody(String email, String password) {
        return "{ \"usernameOrEmail\": \"" + email + "\", \"password\": \"" + password + "\" }";
    }

    private void saveBannedUser() {
        User banned = new User("banned_user", "banned@breadshop.test", passwordEncoder.encode("correct_pass"));
        banned.setActive(false);
        userRepository.save(banned);
    }

    @Test
    @DisplayName("บัญชีถูกแบน + รหัสถูก → 401 บอกว่าถูกระงับ และไม่ได้ cookie")
    void login_bannedAccount_withCorrectPassword_isToldItIsSuspended() throws Exception {
        saveBannedUser();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("banned@breadshop.test", "correct_pass")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("บัญชีนี้ถูกระงับการใช้งาน"))
                .andExpect(cookie().doesNotExist("access_token"))
                .andExpect(cookie().doesNotExist("refresh_token"));
    }

    @Test
    @DisplayName("บัญชีถูกแบน + รหัสผิด → ตอบเหมือนบัญชีที่ไม่มีอยู่ทุกอย่าง (ไม่รั่วว่าบัญชีมีจริง/ถูกแบน)")
    void login_bannedAccount_withWrongPassword_looksExactlyLikeAnUnknownAccount() throws Exception {
        saveBannedUser();

        String banned = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("banned@breadshop.test", "guess")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("nobody@breadshop.test", "guess")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(banned).isEqualTo(unknown);
    }
}
