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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * What creating, updating and deleting an instance LLM connection does — and what it records.
 *
 * <p>Every successful mutation lands on {@code auth_event} through the {@link LlmConnectionAudit} SPI
 * port, and every rejected one lands nowhere: this catalog holds provider credentials, so an audit
 * row for a change that did not happen is as misleading as a missing row for one that did.
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

            // The row that was deleted must be the one that was looked up, and the audit must name
            // that same row — an audit entry naming a different connection than the one removed is
            // worse than none, because the trail then reads as complete while pointing at the wrong
            // provider.
            ArgumentCaptor<LlmConnection> deleted = ArgumentCaptor.forClass(LlmConnection.class);
            verify(connectionRepository).delete(deleted.capture());
            assertThat(deleted.getValue().getId()).isEqualTo(5L);
            assertThat(deleted.getValue().getSlug()).isEqualTo("openai-prod");
            verify(llmConnectionAudit).connectionDeleted(deleted.getValue().getId(), deleted.getValue().getSlug());
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

        private LlmConnection stored() {
            LlmConnection connection = new LlmConnection();
            connection.setId(5L);
            connection.setSlug("openai-prod");
            connection.setDisplayName("Old name");
            connection.setApiKey("sk-stored");
            connection.setEnabled(true);
            when(connectionRepository.findById(5L)).thenReturn(Optional.of(connection));
            when(connectionRepository.save(any(LlmConnection.class))).thenAnswer(inv -> inv.getArgument(0));
            return connection;
        }

        @Test
        void appliesTheSuppliedFieldsAndAuditsTheUpdate() {
            LlmConnection connection = stored();

            LlmConnection saved = connectionService.update(
                5L,
                new UpdateLlmConnectionRequestDTO("New name", null, null, false)
            );

            assertThat(saved.getDisplayName()).isEqualTo("New name");
            assertThat(saved.isEnabled()).isFalse();
            assertThat(saved).isSameAs(connection);
            verify(llmConnectionAudit).connectionUpdated(5L, "openai-prod");
        }

        /** An absent field is "leave it alone", not "set it to null" — the whole point of a PATCH shape. */
        @Test
        void leavesOmittedFieldsUntouched() {
            LlmConnection connection = stored();

            connectionService.update(5L, new UpdateLlmConnectionRequestDTO(null, null, null, null));

            assertThat(connection.getDisplayName()).isEqualTo("Old name");
            assertThat(connection.getApiKey()).isEqualTo("sk-stored");
            assertThat(connection.isEnabled()).isTrue();
        }

        /**
         * Clearing wins over supplying. An admin who ticks "remove the stored key" while the form still
         * carries a value must end up with no credential — the ambiguous request may never leave one
         * behind, and this is the only place that rule is decided.
         */
        @Test
        void clearingTheApiKeyBeatsASuppliedOne() {
            LlmConnection connection = stored();

            connectionService.update(5L, new UpdateLlmConnectionRequestDTO(null, "sk-new", true, null));

            assertThat(connection.getApiKey()).isNull();
        }

        @Test
        void aSuppliedApiKeyReplacesTheStoredOne() {
            LlmConnection connection = stored();

            connectionService.update(5L, new UpdateLlmConnectionRequestDTO(null, "sk-new", false, null));

            assertThat(connection.getApiKey()).isEqualTo("sk-new");
        }

        @Test
        void unknownConnectionRaisesNotFoundWithoutAuditing() {
            when(connectionRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                connectionService.update(404L, new UpdateLlmConnectionRequestDTO("New name", null, null, null))
            ).isInstanceOf(EntityNotFoundException.class);
            verifyNoInteractions(llmConnectionAudit);
        }
    }
}
