package com.breadShop.XXI.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.breadShop.XXI.Util.CookieUtil;
import com.breadShop.XXI.dto.AuthenticationResponse;
import com.breadShop.XXI.dto.CheckEmailRequest;
import com.breadShop.XXI.dto.LoginRequest;
import com.breadShop.XXI.dto.OtpResult;
import com.breadShop.XXI.dto.RegisterRequest;
import com.breadShop.XXI.entity.RefreshToken;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.EmailOtpRepository;
import com.breadShop.XXI.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

//สำหรับการสมัครผ่านหน้าเว็บ   reviewd by peak
@Service
public class AuthService {


    private  final UserRepository userRepository;

    private final EmailOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;


    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsService userDetailsService;
    private  final JwtService jwtService;

    private final Mailservice mailService;

    private final OtpService otpService;

    private final UserActivityLogService activityLogService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
             JwtService jwtService,
             EmailOtpRepository otpRepository,
            Mailservice mailService,
            OtpService otpService,
            RefreshTokenService refreshTokenService,
            UserDetailsService userDetailsService,
            UserActivityLogService activityLogService
    ) {
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.otpService = otpService;
        this.otpRepository = otpRepository;
        this.refreshTokenService = refreshTokenService;
        this.userDetailsService = userDetailsService;
        this.activityLogService = activityLogService;
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest().getRemoteAddr() : null;
    }

    private String getUserAgent() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest().getHeader("User-Agent") : null;
    }

    /**
     * Token Rotation:
     * 1. validate token เดิม
     * 2. ลบ token เดิมออกจาก DB
     * 3. สร้าง access_token ใหม่ + refresh_token ใหม่
     *    → ถ้า refresh_token เก่าถูกขโมยไป พอ rotate แล้วตัวเก่าใช้ไม่ได้อีก
     */
    @Transactional
    public AuthenticationResponse refreshAccessToken(HttpServletRequest request) {
        String refreshTokenValue = CookieUtil.getCookie(request, "refresh_token")
            .orElseThrow(() -> new RuntimeException("NO_REFRESH_TOKEN"));

        // validate ก่อน (throw ถ้า expired/revoked)
        RefreshToken oldRt = refreshTokenService.validate(refreshTokenValue);
        User user = oldRt.getUser();

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        // rotate: ลบเก่า สร้างใหม่
        RefreshToken newRt = refreshTokenService.rotate(oldRt);

        return new AuthenticationResponse(
            jwtService.generateToken(userDetails),  // access token ใหม่
            newRt.getToken(),                        // refresh token ใหม่
            user.getUsername(),
            user.getEmail()
        );
    }

    //อาจจะต้องมาแก้ ยังไม่ได้ test bug
    public void registerUser(RegisterRequest request) {

        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("USERNAME_EXISTS");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("EMAIL_EXISTS");
        }

        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password())
        );

        user.setRole("USER");

        userRepository.save(user);

        mailService.sendWelcomeEmail(request.email(), request.username());

        activityLogService.logSuccess(user, "REGISTER", getClientIp(), getUserAgent(),
                "Register with email: " + request.email());
    }
    

    // ------------------ Login ------------------
    /**
     * เอาไว้รับ login request แล้วส่ง token กลับไป โดยจะตรวจสอบ username กับ password ผ่าน AuthenticationManager 
     * @param request รับ LoginRequest ที่มี usernameOrEmail กับ password
     * @return ส่ง token ,username , email กลับไป
     */
    public AuthenticationResponse loginUser(LoginRequest request) {

        // เช็คก่อนว่า account นี้ใช้ Google Login หรือเปล่า
        // ถ้าใช่ → throw ทันที ไม่ต้องไป authenticate (ป้องกัน "Bad credentials" ที่ไม่ชัดเจน)
        userRepository.findByEmail(request.usernameOrEmail())
                .or(() -> userRepository.findByUsername(request.usernameOrEmail()))
                .ifPresent(u -> {
                    if ("google".equalsIgnoreCase(u.getProvider())) {
                        throw new IllegalArgumentException("GOOGLE_ACCOUNT");
                    }
                });

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.usernameOrEmail(),
                        request.password()
                )
        );
    
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
    
        User user = userRepository
                .findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));
    
        // 1. access token (อายุสั้น)
        String accessToken = jwtService.generateToken(userDetails);
    
        // 2. refresh token (อายุยาว + save DB)
        RefreshToken refreshToken = refreshTokenService.create(user);
    
        activityLogService.logSuccess(user, "LOGIN", getClientIp(), getUserAgent(),
                "Login success for: " + user.getEmail());

        return new AuthenticationResponse(
                accessToken,
                refreshToken.getToken(),
                user.getUsername(),
                user.getEmail()
        );
    }
    

    // ------------------ checkEmail ------------------
    public void checkEmail(CheckEmailRequest request) {

        if (request.email() == null || request.email().isEmpty()) {
            throw new IllegalArgumentException("Email ไม่ควรเป็นค่าว่าง");
        }

        System.out.println("Email ที่รับมา = " + request.email());

        if (userRepository.findByEmail(request.email()).isEmpty()) {
            throw new IllegalArgumentException("ไม่มี Email นี้ ในระบบ");
           
        }

       
    }


     // ------------------ resetPassword ------------------
    /**
     * ใช้ตอน reset password เพื่อเอา password ใหม่ไปเก็บใน DB และจะมีการเปลี่ยนสถานะ token เป็นใช้แล้ว
     * @param token เอา token ไปค้นหา email ที่เกี่ยวข้อง
     * @param newPassword รหัสผ่านใหม่ที่ผู้ใช้ต้องการตั้งที่เช็คเงื่อนไขแล้วจาก frontend 
     */
     @Transactional
     public void resetPassword(String token, String newPassword) {
        String email = otpService.validateResetToken(token);
         
        System.out.println(" eamil ที่ ตรงกับ token ที่ส่งเข้ามา :"+email);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        user.setPassword(passwordEncoder.encode(newPassword));

        otpService.invalidateToken(token);
        
        
     }
 

     // ------------------ seadOTP ------------------
     /**
      * ใช้ตอนส่ง OTP เพื่อ reset password
      * @param email mail ที่จะส่ง OTP เพื่อใช้ reset password
      * @return คืนค่า token ที่ใช้ระบุ OTP ที่สร้างขึ้น
      */
    @Transactional
    public String sendResetPasswordOtp(String email) {

        User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new IllegalArgumentException("EMAIL_NOT_FOUND"));


        otpRepository.deleteByEmailAndPurposeAndUsedFalse(
            email, "RESET_PASSWORD"
        );

        OtpResult result = otpService.generateOtp(email, "RESET_PASSWORD");
        mailService.sendOtpEmail(email, result.plainOtp());

        return result.token();
    }


     // ------------------ verify OTP ------------------
     /**
      * ใช้ตอน verify OTP
      * @param token เอา token ไปค้นหารหัส OTP ที่เก็บไว้
      * @param otp รหัส OTP ที่ผู้ใช้กรอกมา
      * @return คืนค่า token เดิมกลับไป
      */
     @Transactional
    public String verifyOtp(String token ,String otp) {
    
        try {
            otpService.verifyOtp(token, otp);
            System.out.println("Otp ที่ได้รับมา = " + otp);
        } catch (Exception e) {
            System.out.println("เกิดข้อผิดพลาดในการ verify OTP: " + e.getMessage());
                throw new IllegalArgumentException(e.getMessage());
           
        }

        return token;
    }

    
}
