package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.LlmConnectionAudit;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Unit coverage of {@link LlmConnectionService}, including the {@code auth_event} audit wiring (#1368
 * slice 7) through the {@link LlmConnectionAudit} SPI port.
 */
class LlmConnectionServiceTest extends BaseUnitTest {

    @Mock
    private LlmConnectionRepository connectionRepository;

    @Mock
    private LlmModelRepository modelRepository;

    @Mock
    private EgressPolicy egressPolicy;

    @Mock
    private LlmConnectionAudit llmConnectionAudit;

    @InjectMocks
    private LlmConnectionService connectionService;

    private CreateLlmConnectionRequestDTO createRequest() {
        return new CreateLlmConnectionRequestDTO(
            "openai-prod",
            "OpenAI",
            "https://api.openai.com",
            "openai-completions",
            LlmAuthMode.BEARER,
            "sk-abc",
            null
        );
    }

    @Nested
    class Create {

        @Test
        void createdConnectionIsAuditedWithItsIdAndSlug() {
            when(connectionRepository.findBySlug("openai-prod")).thenReturn(Optional.empty());
            when(connectionRepository.save(any(LlmConnection.class))).thenAnswer(inv -> {
                LlmConnection saved = inv.getArgument(0);
                saved.setId(9L);
                return saved;
            });

            LlmConnection result = connectionService.create(createRequest());

            assertThat(result.getId()).isEqualTo(9L);
            verify(llmConnectionAudit).connectionCreated(9L, "openai-prod");
        }

        @Test
        void rejectsDuplicateSlugWithoutAuditing() {
            when(connectionRepository.findBySlug("openai-prod")).thenReturn(Optional.of(new LlmConnection()));

            assertThatThrownBy(() -> connectionService.create(createRequest())).isInstanceOf(
                LlmConnectionSlugConflictException.class
            );

            verify(connectionRepository, never()).save(any());
            verifyNoInteractions(llmConnectionAudit);
        }

        @Test
        void generatesCollisionSafeSlugWhenSlugIsOmitted() {
            CreateLlmConnectionRequestDTO request = new CreateLlmConnectionRequestDTO(
                null,
                "OpenAI",
                "https://api.openai.com",
                "openai-completions",
                LlmAuthMode.BEARER,
                null,
                null
            );
            when(connectionRepository.findBySlug("openai")).thenReturn(Optional.of(new LlmConnection()));
            when(connectionRepository.findBySlug("openai-2")).thenReturn(Optional.empty());
            when(connectionRepository.save(any(LlmConnection.class))).thenAnswer(inv -> inv.getArgument(0));

            LlmConnection result = connectionService.create(request);

            assertThat(result.getSlug()).isEqualTo("openai-2");
        }
    }

    @Nested
    class Delete {

        @Test
        void deletingAnUnreferencedConnectionAuditsTheDeletion() {
            LlmConnection connection = new LlmConnection();
            connection.setId(5L);
            connection.setSlug("openai-prod");
            when(connectionRepository.findById(5L)).thenReturn(Optional.of(connection));
            when(modelRepository.existsByConnectionId(5L)).thenReturn(false);

            connectionService.delete(5L);

            verify(connectionRepository).delete(connection);
            verify(llmConnectionAudit).connectionDeleted(5L, "openai-prod");
        }

        @Test
        void deletionInUseIsRejectedWithoutAuditing() {
            LlmConnection connection = new LlmConnection();
            connection.setId(5L);
            when(connectionRepository.findById(5L)).thenReturn(Optional.of(connection));
            when(modelRepository.existsByConnectionId(5L)).thenReturn(true);

            assertThatThrownBy(() -> connectionService.delete(5L)).isInstanceOf(LlmConnectionInUseException.class);

            verify(connectionRepository, never()).delete(any());
            verifyNoInteractions(llmConnectionAudit);
        }

        @Test
        void unknownConnectionRaisesNotFound() {
            when(connectionRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> connectionService.delete(404L)).isInstanceOf(EntityNotFoundException.class);
            verifyNoInteractions(llmConnectionAudit);
        }
    }

    @Nested
    class Update {

        @Test
        void updatingAConnectionAuditsTheUpdate() {
            LlmConnection connection = new LlmConnection();
            connection.setId(5L);
            connection.setSlug("openai-prod");
            connection.setDisplayName("Old name");
            when(connectionRepository.findById(5L)).thenReturn(Optional.of(connection));
            when(connectionRepository.save(any(LlmConnection.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateLlmConnectionRequestDTO request = new UpdateLlmConnectionRequestDTO("New name", null, null, null);

            connectionService.update(5L, request);

            verify(llmConnectionAudit).connectionUpdated(5L, "openai-prod");
        }
    }
}
