/**
 * Agent-owned SPI for the documentation read path — the dependency-inversion seam that lets the agent
 * mentor/detection pipeline consume a workspace's mirrored Outline documents <em>without</em> reaching into the
 * {@code integration.outline} schema.
 *
 * <p><strong>Why this exists.</strong> The mentor and review content sources need an agent-facing view of the
 * mirrored corpus: a bounded breadth of the workspace's documents for the mentor, and a by-reference lookup for
 * documents linked from the artifact under review. Reading the {@code outline_document} table directly from the
 * agent would be a hidden {@code agent → integration.outline} coupling to another module's <em>physical schema</em>
 * that the Modulith import-check cannot see (it inspects Java imports, not SQL strings).
 *
 * <p><strong>The inversion.</strong> This interface is owned by {@code agent} (the consumer) and
 * <em>implemented</em> by {@code integration.outline} (the table owner). The edge therefore runs
 * {@code integration.outline → agent} — the same direction as the Slack {@code conversation-source} inversion — so
 * no bounded-context cycle forms and a column rename in Outline becomes a compile error <em>inside Outline</em>
 * rather than a silent runtime break in the agent. Exposed to {@code integration.outline} via the
 * {@code documentation-source} named interface.
 */
@org.springframework.modulith.NamedInterface("documentation-source")
package de.tum.cit.aet.hephaestus.agent.documentation;
