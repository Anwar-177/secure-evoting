package com.evoting.config;

import com.evoting.model.Admin;
import com.evoting.repository.AdminRepository;
import com.evoting.service.BlockchainService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the default admin account and the genesis block on first start. */
@Component
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final BlockchainService blockchain;
    private final String adminUsername;
    private final String adminPassword;

    public DataInitializer(AdminRepository adminRepository, PasswordEncoder passwordEncoder,
                           BlockchainService blockchain,
                           @Value("${evoting.admin.username}") String adminUsername,
                           @Value("${evoting.admin.password}") String adminPassword) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.blockchain = blockchain;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (adminRepository.count() == 0) {
            Admin admin = new Admin();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setFullName("Election Authority");
            adminRepository.save(admin);
        }
        blockchain.ensureGenesisBlock();
    }
}
