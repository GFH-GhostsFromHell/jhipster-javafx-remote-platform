package com.gfh.industrial;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudDeploymentGuardTest {
    private static final String ADMIN="correct-horse-demo-admin-2026";
    private static final String VIEWER="correct-horse-demo-viewer-2026";

    private CloudDeploymentGuard guard(boolean cloud,String source,String db,String advertised,String bind) {
        return new CloudDeploymentGuard(cloud,source,db,advertised,bind,ADMIN,VIEWER);
    }

    @Test void refusesOpcInCloud() {
        assertThrows(IllegalStateException.class,()->
            guard(true,"KEPWARE","jdbc:postgresql://localhost:5432/app","https://studio.example.com","0.0.0.0").check());
    }
    @Test void refusesEphemeralDatabaseInCloud() {
        assertThrows(IllegalStateException.class,()->
            guard(true,"SIMULATION","jdbc:h2:mem:test","https://studio.example.com","0.0.0.0").check());
    }
    @Test void refusesInsecureBaseUrl() {
        assertThrows(IllegalStateException.class,()->
            guard(true,"SIMULATION","jdbc:postgresql://localhost:5432/app","http://studio.example.com","0.0.0.0").check());
    }
    @Test void acceptsCloudConfiguratorWhenSafeDefaultsUsed() {
        assertDoesNotThrow(()->
            guard(true,"SIMULATION","jdbc:postgresql://localhost:5432/app","https://studio.example.com","0.0.0.0").check());
    }
    @Test void localDevelopmentStillAllowed() {
        assertDoesNotThrow(()->
            guard(false,"KEPWARE","jdbc:h2:file:./devdb","http://localhost:8090","127.0.0.1").check());
    }
}
