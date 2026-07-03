package com.plantops.iam.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;

@ApplicationScoped
public class PasswordService {

    public String hash(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt());
    }

    public boolean matches(String plain, String hash) {
        if (plain == null || hash == null || hash.isBlank()) {
            return false;
        }
        return BCrypt.checkpw(plain, hash);
    }
}
