package com.app.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Component
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    private Storage storage = new Storage();

    private String frontendUrl;

    private MasterKey masterKey = new MasterKey();

    private Security security = new Security();

    private Admin admin = new Admin();

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public MasterKey getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(MasterKey masterKey) {
        this.masterKey = masterKey;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    @Validated
    public static class Storage {
        @NotBlank
        private String provider;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    @Validated
    public static class MasterKey {
        @NotBlank
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Validated
    public static class Security {
        private Jwt jwt = new Jwt();

        public Jwt getJwt() {
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt;
        }

        @Validated
        public static class Jwt {
            @NotBlank
            private String secret;

            public String getSecret() {
                return secret;
            }

            public void setSecret(String secret) {
                this.secret = secret;
            }
        }
    }

    @Validated
    public static class Admin {
        private String email;
        private String password;
        private String name = "Admin";

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
