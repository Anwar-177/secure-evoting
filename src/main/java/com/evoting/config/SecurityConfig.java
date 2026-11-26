package com.evoting.config;

import com.evoting.repository.AdminRepository;
import com.evoting.repository.VoterRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Locale;

/**
 * Two separate login areas: /admin/** for the election authority and everything else for voters.
 */
@Configuration
public class SecurityConfig {

    private final AdminRepository adminRepository;
    private final VoterRepository voterRepository;

    public SecurityConfig(AdminRepository adminRepository, VoterRepository voterRepository) {
        this.adminRepository = adminRepository;
        this.voterRepository = voterRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurity(HttpSecurity http, PasswordEncoder encoder) throws Exception {
        UserDetailsService admins = username -> adminRepository.findByUsername(username)
                .map(a -> User.withUsername(a.getUsername()).password(a.getPasswordHash()).roles("ADMIN").build())
                .orElseThrow(() -> new UsernameNotFoundException("Admin not found"));

        http.securityMatcher("/admin/**")
                .authenticationProvider(provider(admins, encoder))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .defaultSuccessUrl("/admin/dashboard", true)
                        .failureUrl("/admin/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/?logout"));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain voterSecurity(HttpSecurity http, PasswordEncoder encoder) throws Exception {
        UserDetailsService voters = email -> voterRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(v -> User.withUsername(v.getEmail()).password(v.getPasswordHash()).roles("VOTER").build())
                .orElseThrow(() -> new UsernameNotFoundException("Voter not found"));

        http.authenticationProvider(provider(voters, encoder))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/voter/login", "/voter/register").permitAll()
                        .requestMatchers("/voter/**").hasRole("VOTER")
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/voter/login")
                        .loginProcessingUrl("/voter/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/voter/dashboard", true)
                        .failureUrl("/voter/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/voter/logout")
                        .logoutSuccessUrl("/?logout"));
        return http.build();
    }

    private static DaoAuthenticationProvider provider(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return provider;
    }
}
