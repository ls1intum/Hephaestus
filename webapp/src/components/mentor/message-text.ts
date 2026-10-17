import { shownFeedbackText } from "@/lib/chat-validation";
import { hasText } from "@/lib/text";
import type { ChatMessage } from "@/lib/types";

export function visibleTexts(message: ChatMessage): string[] {
	return message.parts
		.map((part) => (part.type === "text" ? part.text : shownFeedbackText(part)))
		.filter((text): text is string => text !== undefined && hasText(text.trim()));
}
