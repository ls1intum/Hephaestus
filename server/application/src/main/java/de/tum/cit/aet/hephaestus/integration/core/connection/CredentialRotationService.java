package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.SecurityProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@ConditionalOnProperty(prefix = "hephaestus.security", name = "credential-rotation-enabled", havingValue = "true")
public class CredentialRotationService {

    private static final Logger log = LoggerFactory.getLogger(CredentialRotationService.class);

    private final ConnectionRepository connectionRepository;
    private final CredentialBundleConverter converter;
    private final SecurityProperties properties;

    public CredentialRotationService(
            ConnectionRepository connectionRepository,
            CredentialBundleConverter converter,
            SecurityProperties properties) {
        this.connectionRepository = connectionRepository;
        this.converter = converter;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${hephaestus.security.credential-rotation-delay:PT1S}")
    @Transactional
    @WorkspaceAgnostic("Rotates integration credentials across all workspaces")
    public void rotateBatch() {
        List<Long> ids = connectionRepository.lockCredentialRotationBatch(
                converter.activeKeyVersion(), properties.credentialRotationBatchSize());
        if (ids.isEmpty()) {
            return;
        }
        for (Connection connection : connectionRepository.findAllById(ids)) {
            CredentialBundle credentials = connection.credentials(converter).orElseThrow();
            connection.setCredentials(credentials, converter);
        }
        log.info(
                "Credential key rotation progress: rotated={}, activeKeyVersion={}, lastConnectionId={}",
                ids.size(),
                converter.activeKeyVersion(),
                ids.getLast());
    }
}
