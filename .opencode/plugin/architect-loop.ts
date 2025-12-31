import type { Plugin } from "@opencode-ai/plugin"
import type { EventMessageUpdated, AssistantMessage } from "@opencode-ai/sdk"

/**
 * Architect Loop Plugin
 *
 * Sends a stronger prompt than just "Continue" when the architect agent
 * finishes without tool calls. This ensures the architect keeps running
 * its startup/check loop until work is complete.
 */
export const ArchitectLoop: Plugin = async (input) => {
  // Track which messages we've already sent continue prompts for
  // This prevents double-sending when message.updated fires multiple times
  const processedMessages = new Set<string>()

  return {
    event: async ({ event }) => {
      // Only handle message.updated events
      if (event.type !== "message.updated") return

      const info = (event as EventMessageUpdated).properties.info

      // Only trigger for architect agent's assistant messages
      if (info.role !== "assistant") return

      const assistantInfo = info as AssistantMessage
      if (assistantInfo.agent !== "architect") return

      // Only trigger when assistant finishes (not tool-calls, not unknown)
      if (!assistantInfo.finish) return
      if (assistantInfo.finish === "tool-calls" || assistantInfo.finish === "unknown") return

      // Deduplicate: only process each message once
      const messageKey = `${assistantInfo.sessionID}:${assistantInfo.id}`
      if (processedMessages.has(messageKey)) {
        return
      }
      processedMessages.add(messageKey)

      // Cleanup old entries to prevent memory leak (keep last 100)
      if (processedMessages.size > 100) {
        const entries = Array.from(processedMessages)
        for (let i = 0; i < entries.length - 100; i++) {
          processedMessages.delete(entries[i])
        }
      }

      console.log("[architect-loop] Architect finished, sending continue prompt")

      // Send a more directive prompt than just "Continue"
      setTimeout(async () => {
        try {
          await input.client.session.prompt({
            path: { id: assistantInfo.sessionID },
            body: {
              agent: assistantInfo.agent,
              parts: [
                {
                  type: "text",
                  text: `Continue executing your system prompt. Check builder status, review PRs, dispatch work if needed. Run your startup script if you haven't recently.`,
                },
              ],
            },
          })
        } catch (err) {
          console.error("[architect-loop] Failed to continue:", err)
        }
      }, 200)
    },
  }
}
