package de.tum.in.www1.hephaestus.encryption;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Provides access to Spring-managed encryption components from JPA converters.
 *
 * <p>JPA AttributeConverters are instantiated by Hibernate, not Spring, so they
 * cannot directly inject Spring beans. This holder provides a bridge to access
 * the Spring-configured encryptor.</p>
 */
@Component
public class EncryptionContext implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) {
        EncryptionContext.applicationContext = context;
    }

    /**
     * Returns the configured AES-GCM encryptor, or null if Spring context not yet loaded.
     */
    public static AesGcmEncryptor getEncryptor() {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(AesGcmEncryptor.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if the encryption context is fully initialized.
     */
    public static boolean isInitialized() {
        return getEncryptor() != null;
    }
}
