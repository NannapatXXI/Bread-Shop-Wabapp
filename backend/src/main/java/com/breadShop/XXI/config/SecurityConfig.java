package com.breadShop.XXI.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.breadShop.XXI.repository.UserRepository;

@Configuration
//@EnableMethodSecurity
public class SecurityConfig {

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // โหลด user จาก DB
    @Bean
    public UserDetailsService userDetailsService() {
        return login -> userRepository.findByUsername(login)
                .or(() -> userRepository.findByEmail(login))
                .map(user -> {
                    if (!user.isActive()) {
                        throw new UsernameNotFoundException("บัญชีนี้ถูกระงับการใช้งาน");
                    }
                    return org.springframework.security.core.userdetails.User
                            .withUsername(user.getEmail())
                            .password(user.getPassword())
                            .roles(user.getRole().toUpperCase())
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    // Authentication Provider
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of("http://localhost:3000"));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            config.setAllowCredentials(true);
            return config;
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthFilter
    ) throws Exception {
    
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth

            // preflight
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        
            // ---------- Public ----------
            .requestMatchers(
                "/api/v1/auth/login",
                "/api/v1/auth/logout",
                "/api/v1/auth/register",
                "/api/v1/auth/refresh",
                "/api/v1/auth/google/**",
                "/api/v1/auth/send-OTP-mail",
                "/api/v1/auth/verify-otp",
                "/api/v1/auth/reset-password",
                "/api/v1/products",     
                "/api/v1/products/**" ,
                "/uploads/**"
            ).permitAll()
        
            // ---------- Auth required ----------
            .requestMatchers(
                "/api/v1/auth/me",
                "/api/v1/auth/profile"
            ).authenticated()
        
            // ---------- User ----------
            .requestMatchers("/api/v1/users/**").hasRole("USER")

            // ---------- Notifications ----------
            .requestMatchers("/api/v1/notifications/**").authenticated()

            // ---------- Promotions ----------
            .requestMatchers(HttpMethod.GET, "/api/promotions/validate").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/promotions").hasRole("ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/promotions").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/promotions/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/promotions/**").hasRole("ADMIN")

            // ---------- Admin ----------
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
        
            // ---------- Others ----------
            .anyRequest().authenticated()
        )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    
        return http.build();
    }
    
    

}
