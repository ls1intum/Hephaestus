package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
    private WorkspaceAgentBindingRepository agentBindingRepository;

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
        // Stubs both: create()/update() now flush synchronously (saveAndFlush, so a
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

    /** A rate column from the table below; an absent rate is a {@code null} rate, not zero. */
    private static @org.jspecify.annotations.Nullable BigDecimal rate(@org.jspecify.annotations.Nullable String value) {
        return value == null ? null : new BigDecimal(value);
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
            when(priceRepository.saveAndFlush(any(LlmModelPrice.class))).thenAnswer(invocation ->
                invocation.getArgument(0)
            );
            when(priceRepository.save(any(LlmModelPrice.class))).thenAnswer(invocation -> invocation.getArgument(0));

            modelService.updatePrice(7L, pricedRequest("3.00", "9.00"));

            // The close must reach the database BEFORE the insert, not merely before commit.
            // ux_llm_model_price_open allows one open row per model, and the new row is
            // IDENTITY-generated (its INSERT fires at persist, ahead of any pending UPDATE), so a
            // deferred close would make the reprice collide with the row it is superseding. Hence
            // saveAndFlush on the closing row, asserted in order here.
            ArgumentCaptor<LlmModelPrice> closed = ArgumentCaptor.forClass(LlmModelPrice.class);
            ArgumentCaptor<LlmModelPrice> inserted = ArgumentCaptor.forClass(LlmModelPrice.class);
            InOrder inOrder = inOrder(priceRepository);
            inOrder.verify(priceRepository).saveAndFlush(closed.capture());
            inOrder.verify(priceRepository).save(inserted.capture());
            verify(priceRepository, never()).save(open);

            assertThat(closed.getValue()).isSameAs(open);
            assertThat(closed.getValue().getEffectiveTo()).isNotNull();

            assertThat(inserted.getValue()).isNotSameAs(open);
            assertThat(inserted.getValue().getEffectiveTo()).isNull();
            assertThat(inserted.getValue().getPer1mInputUsd()).isEqualByComparingTo("3.00");
        }

        /**
         * The shared {@code LlmPriceValidation} rule table, driven through the endpoint that reprices
         * an instance model. One row per rule, so deleting any single branch of the validator leaves
         * exactly one row red. The message fragment is asserted and not merely the exception type:
         * it is what the admin who typed the rates reads back.
         *
         * <p>Workspace BYO models reach the same validator through their own create path;
         * {@code WorkspaceLlmModelServiceTest} pins that wiring rather than repeating this table.
         */
        @ParameterizedTest(name = "{5}")
        @CsvSource(
            nullValues = "NULL",
            value = {
                "PRICED, 3.00, NULL, NULL, an input rate and an output rate, a price missing its output rate",
                "PRICED, -1.00, 2.00, NULL, zero or greater, a negative rate",
                "PRICED, 0, 0, NULL, choose Free instead, an all-zero price that would bill as verified $0 forever",
                "NO_CHARGE, NULL, NULL, NULL, note, a free model with no explanation",
                "UNPRICED, 1.00, NULL, NULL, clear them or set a price, rates carried by a model with no price",
            }
        )
        void updatePriceRejectsAnInvalidRateCombination(
            PricingMode pricingMode,
            String per1mInputUsd,
            String per1mOutputUsd,
            String priceNote,
            String expectedMessage,
            String why
        ) {
            UpdateLlmModelPriceRequestDTO request = new UpdateLlmModelPriceRequestDTO(
                pricingMode,
                rate(per1mInputUsd),
                rate(per1mOutputUsd),
                null,
                null,
                priceNote
            );

            assertThatThrownBy(() -> modelService.updatePrice(7L, request))
                .as(why)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
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

        /**
         * Replace-all grant updates must read the row through the write-locked finder, or two admins
         * editing the same model's grant set at once silently overwrite each other. Asserted on the
         * instance that actually gets mutated rather than on which finder was called: the two finders
         * hand back different objects here, so a read that skipped the lock would write its visibility
         * onto the unlocked copy and return that one instead.
         */
        @Test
        void locksTheModelWhileReplacingItsGrantSet() {
            stubModelSavePassthrough();
            // A decoy behind the NON-locking finder. lenient() because it must stay unused while the
            // code is correct — that is the assertion. If updateSharing ever reads through
            // findByIdWithConnection instead, it picks this object up and the assertions below fail.
            LlmModel unlockedCopy = new LlmModel();
            unlockedCopy.setId(7L);
            unlockedCopy.setVisibility(ModelVisibility.PUBLIC);
            lenient().when(modelRepository.findByIdWithConnection(7L)).thenReturn(Optional.of(unlockedCopy));
            when(grantRepository.findByIdModelId(7L)).thenReturn(List.of());

            LlmModel result = modelService.updateSharing(
                7L,
                new UpdateLlmModelSharingRequestDTO(ModelVisibility.GRANTED, List.of())
            );

            assertThat(result).as("the row that was mutated and saved must be the write-locked one").isSameAs(model);
            assertThat(model.getVisibility()).isEqualTo(ModelVisibility.GRANTED);
            assertThat(unlockedCopy.getVisibility())
                .as("the unlocked read must not be the one that gets written")
                .isEqualTo(ModelVisibility.PUBLIC);
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

            LlmModel result = modelService.updateSharing(
                7L,
                new UpdateLlmModelSharingRequestDTO(ModelVisibility.GRANTED, List.of())
            );

            assertThat(result.getVisibility())
                .as("an empty grant list still means GRANTED — shared with nobody, not made public")
                .isEqualTo(ModelVisibility.GRANTED);
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

        // The rejection half of this guard — a second model reusing an upstream id already taken on the
        // connection — is asserted end to end, together with the 409 ProblemDetail it must produce, by
        // LlmModelAdminControllerIntegrationTest#aDuplicateUpstreamModelIdOnTheSameConnectionIs409WithAProblemDetail.

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
            // An edit to a model every workspace can bind has to leave a trail naming which model on
            // which connection changed — a CREATED row here, or a row naming a different model, reads
            // as a complete audit trail while pointing at the wrong provider.
            verify(llmModelAudit).modelUpdated(7L, 3L, "gpt-5");
            verify(llmModelAudit, never()).modelCreated(any(), any(), any());
        }

        /**
         * A model is created switched off, always. Activation requires a price, and the price is set on
         * a separate call that needs the model's id — so a model that arrived live could only ever be a
         * live model with no price, which admission then refuses with nothing on screen to explain it.
         */
        @Test
        void refusesToCreateAModelThatIsAlreadyActive() {
            CreateLlmModelRequestDTO active = new CreateLlmModelRequestDTO(
                "gpt-5-eu",
                "GPT-5 EU",
                "gpt-5",
                null,
                null,
                null,
                true
            );

            assertThatThrownBy(() -> modelService.create(3L, active))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Create the model disabled, set its price, then activate it.");

            // Refused before anything is looked up, let alone written or audited.
            verify(connectionRepository, never()).findById(any());
            verify(modelRepository, never()).saveAndFlush(any());
            verifyNoInteractions(llmModelAudit);
        }

        /**
         * the fast-path {@code existsByConnectionIdAndUpstreamModelId} check above is
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
        void deletingAModelStillBoundToAnAgentBindingIsRejected() {
            when(agentBindingRepository.existsByInstanceModelId(7L)).thenReturn(true);

            assertThatThrownBy(() -> modelService.delete(7L)).isInstanceOf(LlmModelInUseException.class);
            verify(modelRepository, never()).delete(any());
        }

        @Test
        void deletingAnUnboundModelSucceeds() {
            when(agentBindingRepository.existsByInstanceModelId(7L)).thenReturn(false);

            modelService.delete(7L);

            verify(modelRepository).delete(model);
            verify(llmModelAudit).modelDeleted(7L, 3L, "gpt-5");
        }

        @Test
        void deletingAModelDoesNotAuditWhenTheGuardRejectsIt() {
            when(agentBindingRepository.existsByInstanceModelId(7L)).thenReturn(true);

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
