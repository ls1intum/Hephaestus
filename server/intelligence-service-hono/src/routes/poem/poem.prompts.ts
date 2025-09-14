import { langfuse } from "@/lib/langfuse";

const POEM_PROMPT_NAME = "poem-generator" as const;

const initialPrompt = `Write a short poem about {{topic}} {{style}}. Keep it under 8 lines.`;

export async function getPoemPrompt() {
  return await langfuse.prompt.get(POEM_PROMPT_NAME)
    .catch(async (e: Error) => {
      if (e.message.includes("LangfuseNotFoundError")) {
        console.log(`Prompt "${POEM_PROMPT_NAME}" not found on LangFuse. Creating...`);
        return await langfuse.prompt.create({
          name: POEM_PROMPT_NAME,
          type: "text",
          prompt: initialPrompt,
          labels: ["production"],
        });
      } else {
        throw e;
      }
    })
    .catch(async (e: Error) => {
      return await langfuse.prompt.get(POEM_PROMPT_NAME, { fallback: initialPrompt });
    });
}