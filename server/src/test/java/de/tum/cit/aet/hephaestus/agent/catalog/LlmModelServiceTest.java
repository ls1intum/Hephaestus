package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.LlmModelAudit;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DataIntegrityViolationException;

class LlmModelServiceTest extends BaseUnitTest {

    @Mock
    private LlmModelRepository modelRepository;

    @Mock
    private LlmConnectionRepository connectionRepository;

    @Mock
    private LlmModelPriceRepository priceRepository;

    @Mock
    private LlmModelWorkspaceGrantRepository grantRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private LlmModelAudit llmModelAudit;

    @InjectMocks
    private LlmModelService modelService;

    private LlmModel model;

    @BeforeEach
    void setUp() {
        model = new LlmModel();
        model.setId(7L);
        model.setSlug("gpt-5");
        model.setDisplayName("GPT-5");
        model.setUpstreamModelId("gpt-5");
        LlmConnection connection = new LlmConnection();
        connection.setId(3L);
        model.setConnection(connection);
        // Not every test looks up model 7 (e.g. the unknown-id 404 case) — lenient so those aren't
        // flagged as unnecessary stubbing. Both finders are stubbed: updatePrice() still uses the plain
        // findById, get() uses the eager-fetch variant, and activation/repricing/sharing use the
        // write-locked variant.
        lenient().when(modelRepository.findById(7L)).thenReturn(Optional.of(model));
        lenient().when(modelRepository.findByIdWithConnection(7L)).thenReturn(Optional.of(model));
        lenient().when(modelRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(model));
    }

    private void stubModelSavePassthrough() {
        // Stubs both: create()/update() now flush synchronously (saveAndFlush — #1368 fix wave, so a
        // concurrent unique-constraint violation surfaces inside their try/catch instead of escaping as
        // an uncaught 500 at the transaction's implicit end-of-method flush), while updateSharing()
        // (untouched — it never changes upstream_model_id) still calls plain save().
        lenient()
            .when(modelRepository.save(any(LlmModel.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
            .when(modelRepository.saveAndFlush(any(LlmModel.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private UpdateLlmModelPriceRequestDTO pricedRequest(String input, String output) {
        return new UpdateLlmModelPriceRequestDTO(
            PricingMode.PRICED,
            new BigDecimal(input),
            new BigDecimal(output),
            null,
            null,
            null,
            null
        );
    }

    @Nested
    class PriceSupersede {

        @Test
        void repricingWithNoExistingPriceOnlyInsertsTheNewOpenRow() {
            when(priceRepository.findByModelIdAndEffectiveToIsNull(7L)).thenReturn(Optional.empty());
            when(priceRepository.save(any(LlmModelPrice.class))).thenAnswer(invocation -> invocation.getArgument(0));

            LlmModelPrice result = modelService.updatePrice(7L, pricedRequest("3.00", "9.00"));

            verify(priceRepository, times(1)).save(any(LlmModelPrice.class));
            assertThat(result.getPricingMode()).isEqualTo(PricingMode.PRICED);
            assertThat(result.getPer1mInputUsd()).isEqualByComparingTo("3.00");
            assertThat(result.getEffectiveTo()).isNull();

            // Never the rate itself passed to the audit port — just enough to say what changed, never a
            // value that could be mistaken for credential material.
            verify(llmModelAudit).modelPriceChanged(7L, "PRICED");
        }

        @Test
        void repricingClosesThePriorOpenRowAndInsertsANewOne() {
            LlmModelPrice open = new LlmModelPrice();
            open.setId(1L);
            open.setModel(model);
            open.setPricingMode(PricingMode.PRICED);
            open.setPer1mInputUsd(new BigDecimal("1.00"));
            open.setPer1mOutputUsd(new BigDecimal("2.00"));
            when(priceRepository.findByModelIdAndEffectiveToIsNull(7L)).thenReturn(Optional.of(open));
            when(priceRepository.save(any(LlmModelPrice.class))).thenAnswer(invocation -> invocation.getArgument(0));

            modelService.updatePrice(7L, pricedRequest("3.00", "9.00"));

            ArgumentCaptor<LlmModelPrice> savedCaptor = ArgumentCaptor.forClass(LlmModelPrice.class);
            verify(priceRepository, times(2)).save(savedCaptor.capture());
            List<LlmModelPrice> saved = savedCaptor.getAllValues();

            // First save closes the previously-open row.
            assertThat(saved.get(0)).isSameAs(open);
            assertThat(saved.get(0).getEffectiveTo()).isNotNull();

            // Second save is the new open row.
            assertThat(saved.get(1)).isNotSameAs(open);
            assertThat(saved.get(1).getEffectiveTo()).isNull();
            assertThat(saved.get(1).getPer1mInputUsd()).isEqualByComparingTo("3.00");
        }

        @Test
        void pricedModeRequiresBothInputAndOutputRates() {
            UpdateLlmModelPriceRequestDTO request = new UpdateLlmModelPriceRequestDTO(
                PricingMode.PRICED,
                new BigDecimal("3.00"),
                null,
                null,
                null,
                null,
                null
            );

            assertThatThrownBy(() -> modelService.updatePrice(7L, request)).isInstanceOf(
                IllegalArgumentException.class
            );
            verify(priceRepository, never()).save(any());
        }

        @Test
        void pricedModeRejectsNegativeRates() {
            UpdateLlmModelPriceRequestDTO request = new UpdateLlmModelPriceRequestDTO(
                PricingMode.PRICED,
                new BigDecimal("-1.00"),
                new BigDecimal("2.00"),
                null,
                null,
                null,
                null
            );

            assertThatThrownBy(() -> modelService.updatePrice(7L, request)).isInstanceOf(
                IllegalArgumentException.class
            );
            verify(priceRepository, never()).save(any());
        }

        @Test
        void pricedModeRejectsAllZeroRates() {
            // #1368 fix wave: an all-zero-rate PRICED model would otherwise pass validation and
            // count as verified $0 spend forever — that's what Free is for.
            UpdateLlmModelPriceRequestDTO request = new UpdateLlmModelPriceRequestDTO(
                PricingMode.PRICED,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null
            );

            assertThatThrownBy(() -> modelService.updatePrice(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("choose Free instead");
            verify(priceRepository, never()).save(any());
        }

        @Test
        void pricedModeAcceptsOneZeroRateAsLongAsAnotherIsPositive() {
            when(priceRepository.findByModelIdAndEffectiveToIsNull(7L)).thenReturn(Optional.empty());
            when(priceRepository.save(any(LlmModelPrice.class))).thenAnswer(invocation -> invocation.getArgument(0));
            // Free-input, priced-output is a legitimate PRICED model — only the all-zero case is rejected.
            UpdateLlmModelPriceRequestDTO request = new UpdateLlmModelPriceRequestDTO(
                PricingMode.PRICED,
                BigDecimal.ZERO,
                new BigDecimal("9.00"),
                null,
                null,
                null,
                null
            );

            LlmModelPrice result = modelService.updatePrice(7L, request);

            assertThat(result.getPricingMode()).isEqualTo(PricingMode.PRICED);
        }

        @Test
        void freeModeRequiresANote() {
            UpdateLlmModelPriceRequestDTO request = new UpdateLlmModelPriceRequestDTO(
                PricingMode.NO_CHARGE,
                null,
                null,
                null,
                null,
                null,
                null
            );

            assertThatThrownBy(() -> modelService.updatePrice(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("note");
            verify(priceRepository, never()).save(any());
        }

        @Test
        void freeModeWithNoteAndNoRatesSucceeds() {
            when(priceRepository.findByModelIdAndEffectiveToIsNull(7L)).thenReturn(Optional.empty());
            when(priceRepository.save(any(LlmModelPrice.class))).thenAnswer(invocation -> invocation.getArgument(0));
            UpdateLlmModelPriceRequestDTO request = new UpdateLlmModelPriceRequestDTO(
                PricingMode.NO_CHARGE,
                null,
                null,
                null,
                null,
                null,
                "Self-hosted, internally funded"
            );

            LlmModelPrice result = modelService.updatePrice(7L, request);

            assertThat(result.getPricingMode()).isEqualTo(PricingMode.NO_CHARGE);
            assertThat(result.getNote()).isEqualTo("Self-hosted, internally funded");
        }

        @Test
        void unpricedModeRejectsRates() {
            UpdateLlmModelPriceRequestDTO request = new UpdateLlmModelPriceRequestDTO(
                PricingMode.UNPRICED,
                new BigDecimal("1.00"),
                null,
                null,
                null,
                null,
                null
            );

            assertThatThrownBy(() -> modelService.updatePrice(7L, request)).isInstanceOf(
                IllegalArgumentException.class
            );
            verify(priceRepository, never()).save(any());
        }

        @Test
        void enabledModelCannotBeRepricedToUnpricedAndExistingPriceStaysOpen() {
            model.setEnabled(true);
            UpdateLlmModelPriceRequestDTO request = new UpdateLlmModelPriceRequestDTO(
                PricingMode.UNPRICED,
                null,
                null,
                null,
                null,
                null,
                null
            );

            assertThatThrownBy(() -> modelService.updatePrice(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Disable the model");

            verifyNoInteractions(priceRepository);
        }

        @Test
        void unknownModelRaisesNotFound() {
            when(modelRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> modelService.updatePrice(404L, pricedRequest("1.00", "2.00"))).isInstanceOf(
                EntityNotFoundException.class
            );
        }
    }

    @Nested
    class SharingReplace {

        @Test
        void locksTheModelWhileReplacingItsGrantSet() {
            stubModelSavePassthrough();
            when(grantRepository.findByIdModelId(7L)).thenReturn(List.of());

            modelService.updateSharing(7L, new UpdateLlmModelSharingRequestDTO(ModelVisibility.GRANTED, List.of()));

            verify(modelRepository).findByIdForUpdate(7L);
            verify(modelRepository, never()).findByIdWithConnection(7L);
        }

        @Test
        void publicVisibilityDeletesAllExistingGrants() {
            stubModelSavePassthrough();
            LlmModelWorkspaceGrant existing = new LlmModelWorkspaceGrant(7L, 1L);
            when(grantRepository.findByIdModelId(7L)).thenReturn(List.of(existing));

            LlmModel result = modelService.updateSharing(
                7L,
                new UpdateLlmModelSharingRequestDTO(ModelVisibility.PUBLIC, null)
            );

            verify(grantRepository).deleteAll(List.of(existing));
            verify(grantRepository, never()).saveAll(anyCollection());
            assertThat(result.getVisibility()).isEqualTo(ModelVisibility.PUBLIC);

            verify(llmModelAudit).modelSharingChanged(7L, "PUBLIC", 0);
        }

        @Test
        void grantedVisibilityRejectsUnknownWorkspaceIds() {
            when(grantRepository.findByIdModelId(7L)).thenReturn(List.of());
            when(workspaceRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(workspaceWithId(1L)));

            UpdateLlmModelSharingRequestDTO request = new UpdateLlmModelSharingRequestDTO(
                ModelVisibility.GRANTED,
                List.of(1L, 2L)
            );

            assertThatThrownBy(() -> modelService.updateSharing(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2");
            verify(grantRepository, never()).deleteAll(anyCollection());
            verify(grantRepository, never()).saveAll(anyCollection());
        }

        @Test
        void grantedVisibilityReplacesGrantSetWithExactlyTheRequestedWorkspaces() {
            stubModelSavePassthrough();
            // Existing grants: workspace 1 (kept) and workspace 2 (to be removed).
            LlmModelWorkspaceGrant keep = new LlmModelWorkspaceGrant(7L, 1L);
            LlmModelWorkspaceGrant remove = new LlmModelWorkspaceGrant(7L, 2L);
            when(grantRepository.findByIdModelId(7L)).thenReturn(List.of(keep, remove));
            when(workspaceRepository.findAllById(Set.of(1L, 3L))).thenReturn(
                List.of(workspaceWithId(1L), workspaceWithId(3L))
            );

            UpdateLlmModelSharingRequestDTO request = new UpdateLlmModelSharingRequestDTO(
                ModelVisibility.GRANTED,
                List.of(1L, 3L)
            );

            LlmModel result = modelService.updateSharing(7L, request);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LlmModelWorkspaceGrant>> removeCaptor = ArgumentCaptor.forClass(List.class);
            verify(grantRepository).deleteAll(removeCaptor.capture());
            assertThat(removeCaptor.getValue()).containsExactly(remove);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LlmModelWorkspaceGrant>> addCaptor = ArgumentCaptor.forClass(List.class);
            verify(grantRepository).saveAll(addCaptor.capture());
            assertThat(addCaptor.getValue())
                .extracting(grant -> grant.getId().getWorkspaceId())
                .containsExactly(3L);

            assertThat(result.getVisibility()).isEqualTo(ModelVisibility.GRANTED);
        }

        @Test
        void grantedVisibilityWithEmptyRequestClearsAllGrantsWithoutValidatingWorkspaces() {
            stubModelSavePassthrough();
            LlmModelWorkspaceGrant existing = new LlmModelWorkspaceGrant(7L, 1L);
            when(grantRepository.findByIdModelId(7L)).thenReturn(List.of(existing));

            modelService.updateSharing(7L, new UpdateLlmModelSharingRequestDTO(ModelVisibility.GRANTED, List.of()));

            verify(workspaceRepository, never()).findAllById(anyCollection());
            verify(grantRepository).deleteAll(List.of(existing));
            verify(grantRepository, never()).saveAll(anyCollection());
        }
    }

    @Nested
    class UpstreamIdConflict {

        private CreateLlmModelRequestDTO createRequest(String upstreamModelId) {
            return new CreateLlmModelRequestDTO("gpt-5-eu", "GPT-5 EU", upstreamModelId, null, null, null, null);
        }

        @Test
        void metadataUpdateRevalidatesAnEnabledModel() {
            model.setEnabled(true);
            model.getConnection().setEnabled(true);
            when(priceRepository.findByModelIdAndEffectiveToIsNull(7L)).thenReturn(Optional.empty());
            UpdateLlmModelRequestDTO request = new UpdateLlmModelRequestDTO("Renamed", null, null, null, null);

            assertThatThrownBy(() -> modelService.update(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configure a price");
            verify(modelRepository, never()).saveAndFlush(any());
        }

        @Test
        void createRejectsADuplicateUpstreamIdOnTheSameConnection() {
            LlmConnection connection = new LlmConnection();
            connection.setId(3L);
            when(connectionRepository.findById(3L)).thenReturn(Optional.of(connection));
            when(modelRepository.findByConnectionIdAndSlug(3L, "gpt-5-eu")).thenReturn(Optional.empty());
            when(modelRepository.existsByConnectionIdAndUpstreamModelId(3L, "gpt-5")).thenReturn(true);

            assertThatThrownBy(() -> modelService.create(3L, createRequest("gpt-5"))).isInstanceOf(
                LlmModelUpstreamIdConflictException.class
            );
            verify(modelRepository, never()).saveAndFlush(any());
        }

        @Test
        void createSucceedsWhenTheUpstreamIdIsUniqueOnTheConnection() {
            LlmConnection connection = new LlmConnection();
            connection.setId(3L);
            when(connectionRepository.findById(3L)).thenReturn(Optional.of(connection));
            when(modelRepository.findByConnectionIdAndSlug(3L, "gpt-5-eu")).thenReturn(Optional.empty());
            when(modelRepository.existsByConnectionIdAndUpstreamModelId(3L, "gpt-5")).thenReturn(false);
            stubModelSavePassthrough();

            LlmModel result = modelService.create(3L, createRequest("gpt-5"));

            assertThat(result.getUpstreamModelId()).isEqualTo("gpt-5");
        }

        @Test
        void updateKeepsImmutableUpstreamModelId() {
            stubModelSavePassthrough();
            UpdateLlmModelRequestDTO request = new UpdateLlmModelRequestDTO("Renamed", null, null, null, null);

            LlmModel result = modelService.update(7L, request);

            assertThat(result.getUpstreamModelId()).isEqualTo("gpt-5");
            verify(modelRepository, never()).existsByConnectionIdAndUpstreamModelIdAndIdNot(any(), any(), any());
        }

        /**
         * #1368 fix wave: the fast-path {@code existsByConnectionIdAndUpstreamModelId} check above is
         * racy — two concurrent creates/updates can both pass it. The unique constraint
         * {@code ux_llm_model_connection_upstream} is the real backstop, but it only fires when the
         * INSERT/UPDATE is actually flushed to the DB. {@code save()} alone doesn't guarantee that (a
         * generated-id entity's write can be deferred to the transaction's implicit end-of-method flush,
         * OUTSIDE the try/catch) — {@code saveAndFlush()} forces it synchronously, so the violation lands
         * inside the catch and becomes a 409 instead of an uncaught 500. Simulated here via a mocked
         * {@link DataIntegrityViolationException} thrown directly from {@code saveAndFlush}.
         */
        @Test
        void createTranslatesAFlushTimeConstraintViolationInto409() {
            LlmConnection connection = new LlmConnection();
            connection.setId(3L);
            when(connectionRepository.findById(3L)).thenReturn(Optional.of(connection));
            when(modelRepository.findByConnectionIdAndSlug(3L, "gpt-5-eu")).thenReturn(Optional.empty());
            when(modelRepository.existsByConnectionIdAndUpstreamModelId(3L, "gpt-5")).thenReturn(false);
            when(modelRepository.saveAndFlush(any(LlmModel.class))).thenThrow(upstreamIdConstraintViolation());

            assertThatThrownBy(() -> modelService.create(3L, createRequest("gpt-5"))).isInstanceOf(
                LlmModelUpstreamIdConflictException.class
            );
        }

        private DataIntegrityViolationException upstreamIdConstraintViolation() {
            org.hibernate.exception.ConstraintViolationException cve =
                new org.hibernate.exception.ConstraintViolationException(
                    "duplicate",
                    null,
                    "ux_llm_model_connection_upstream"
                );
            return new DataIntegrityViolationException("duplicate", cve);
        }
    }

    @Nested
    class Deletion {

        @Test
        void deletingAModelStillBoundToAnAgentConfigIsRejected() {
            when(agentConfigRepository.existsByInstanceModelId(7L)).thenReturn(true);

            assertThatThrownBy(() -> modelService.delete(7L)).isInstanceOf(LlmModelInUseException.class);
            verify(modelRepository, never()).delete(any());
        }

        @Test
        void deletingAnUnboundModelSucceeds() {
            when(agentConfigRepository.existsByInstanceModelId(7L)).thenReturn(false);

            modelService.delete(7L);

            verify(modelRepository).delete(model);
            verify(llmModelAudit).modelDeleted(7L, 3L, "gpt-5");
        }

        @Test
        void deletingAModelDoesNotAuditWhenTheGuardRejectsIt() {
            when(agentConfigRepository.existsByInstanceModelId(7L)).thenReturn(true);

            assertThatThrownBy(() -> modelService.delete(7L)).isInstanceOf(LlmModelInUseException.class);

            verifyNoInteractions(llmModelAudit);
        }
    }

    private static Workspace workspaceWithId(Long id) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        return workspace;
    }
}
