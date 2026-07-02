/**
 * AI-SDK wire chunks streamed for a mentor turn. Part of the {@code mentor-chat} Modulith named interface so a
 * non-{@code agent} channel adapter (e.g. the Slack streaming adapter) can pattern-match the chunk stream —
 * a package-level declaration covers {@code UIMessageChunk} and all its nested permitted records.
 */
@org.springframework.modulith.NamedInterface("mentor-chat")
package de.tum.cit.aet.hephaestus.agent.mentor.chat.wire;
