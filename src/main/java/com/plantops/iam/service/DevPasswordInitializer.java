package com.plantops.iam.service;

import com.plantops.iam.config.IamSecurityConfig;
import com.plantops.iam.entity.AppUserEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DevPasswordInitializer {

    @Inject
    IamSecurityConfig securityConfig;

    @Inject
    PasswordService passwordService;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (!securityConfig.devMode()) {
            return;
        }
        AppUserEntity dev = AppUserEntity.findById("dev");
        if (dev != null && (dev.passwordHash == null || dev.passwordHash.isBlank())) {
            dev.passwordHash = passwordService.hash("dev");
        }
    }
}
