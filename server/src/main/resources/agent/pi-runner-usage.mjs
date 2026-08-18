// What one agent session spent, counted so that compaction cannot un-count it.
//
// Its own module for the same reason pi-runner-timings.mjs is: pi-runner.mjs reads /workspace and the
// environment at module scope, so the only way to exercise these rules is to have them somewhere a test
// can call. The rules here decide a bill, which is the strongest reason yet to be able to.

/**
 * A running total of what one session has spent, built from the event stream rather than from the
 * session's message list.
 *
 * Why not just read session.messages: compaction is on for every session we create, and a compacted
 * assistant message is gone from that list along with its usage block. A walk of the survivors therefore
 * reports whatever compaction happened to leave behind — on a long fan-out that was a quarter of the
 * calls actually made, and the server billed the shortfall because it preferred this report over the
 * proxy's count. A message counted here at message_end can never be un-counted, whatever the session
 * does with it afterwards.
 */
export function newUsageLedger() {
    return {
        model: null,
        inputTokens: 0,
        outputTokens: 0,
        reasoningTokens: 0,
        cacheReadTokens: 0,
        cacheWriteTokens: 0,
        costUsd: 0,
        totalCalls: 0,
        assistantMessages: 0,
        stopReasons: {},
        seenIds: new Set(),
    };
}

export function addAssistantUsage(ledger, msg) {
    if (!msg || msg.role !== "assistant" || !msg.usage) return;
    // message_end fires once per message, so this is belt and braces — but a redelivered event would
    // otherwise double a real bill, and over-billing is the one error direction this whole change exists
    // to avoid creating.
    if (msg.id != null) {
        if (ledger.seenIds.has(msg.id)) return;
        ledger.seenIds.add(msg.id);
    }
    ledger.assistantMessages++;
    ledger.totalCalls++;
    ledger.model = msg.model || ledger.model;
    ledger.inputTokens += Number(msg.usage.input || 0);
    ledger.outputTokens += Number(msg.usage.output || 0);
    ledger.reasoningTokens += Number(msg.usage.reasoning || msg.usage.reasoningTokens || 0);
    ledger.cacheReadTokens += Number(msg.usage.cacheRead || 0);
    ledger.cacheWriteTokens += Number(msg.usage.cacheWrite || 0);
    ledger.costUsd += Number(msg.usage.cost?.total || 0);
    const sr = msg.stopReason || "unknown";
    ledger.stopReasons[sr] = (ledger.stopReasons[sr] || 0) + 1;
}

/**
 * What this session has spent so far: the larger of the two views of it, per bucket.
 *
 * The stream ledger is the one that survives compaction, and the message walk is the one that survives
 * an SDK that does not put a usage block on the event. Taking the maximum means neither assumption has
 * to hold for the report to be no worse than it was before, and when both hold the report is right.
 *
 * @param streamLedger the session's ledger, or null for a session nothing subscribed to
 */
export function extractUsageFromSession(session, streamLedger = null) {
    const messages = session.messages || [];
    const walked = newUsageLedger();
    for (const msg of messages) {
        // Responses-path shape (output_tokens_details.reasoning_tokens) surfaced by the SDK as
        // usage.reasoning when the upstream model reports it (e.g. o-series/gpt-5 reasoning models);
        // absent for chat/completions-only models, so that bucket stays 0 for those.
        addAssistantUsage(walked, msg);
    }
    const source = streamLedger ?? walked;

    return {
        model: source.model || walked.model,
        inputTokens: Math.max(walked.inputTokens, source.inputTokens),
        outputTokens: Math.max(walked.outputTokens, source.outputTokens),
        reasoningTokens: Math.max(walked.reasoningTokens, source.reasoningTokens),
        cacheReadTokens: Math.max(walked.cacheReadTokens, source.cacheReadTokens),
        cacheWriteTokens: Math.max(walked.cacheWriteTokens, source.cacheWriteTokens),
        costUsd: Math.max(walked.costUsd, source.costUsd),
        totalCalls: Math.max(walked.totalCalls, source.totalCalls),
        assistantMessages: Math.max(walked.assistantMessages, source.assistantMessages),
        stopReasons: source.assistantMessages >= walked.assistantMessages ? source.stopReasons : walked.stopReasons,
    };
}

