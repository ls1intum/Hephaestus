import { cn } from "@/lib/utils";
import { useEffect, useRef, useState } from "react";

import { Textarea } from "@/components/ui/textarea";

interface Props extends React.ComponentProps<"textarea"> {
	maxHeight?: number;
	placeholder?: string;
	onPaste?: (event: React.ClipboardEvent<HTMLTextAreaElement>) => void;
	onEnter?: (event: React.KeyboardEvent<HTMLTextAreaElement>) => void;
}

const AutoResizeTextarea = ({
	maxHeight,
	onPaste,
	onEnter,
	placeholder,
	className,
	...props
}: Props) => {
	const textareaRef = useRef<HTMLTextAreaElement>(null);
	const [isComposing, setIsComposing] = useState(false);

	useEffect(() => {
		const textarea = textareaRef.current;
		if (!textarea || !onPaste) return;

		const handlePaste = (event: ClipboardEvent) => {
			// Convert native ClipboardEvent to React.ClipboardEvent-like object
			const reactEvent = {
				...event,
				nativeEvent: event,
				isDefaultPrevented: () => event.defaultPrevented,
				isPropagationStopped: () => false,
				persist: () => {},
				currentTarget: event.currentTarget as HTMLTextAreaElement,
				target: event.target as HTMLTextAreaElement,
			} as React.ClipboardEvent<HTMLTextAreaElement>;

			onPaste(reactEvent);
		};

		textarea.addEventListener("paste", handlePaste);

		return () => {
			textarea.removeEventListener("paste", handlePaste);
		};
	}, [onPaste]);

	useEffect(() => {
		const textarea = textareaRef.current;
		if (!textarea || !maxHeight) return;
		textarea.style.height = "40px";
		const newHeight = Math.min(textarea.scrollHeight, maxHeight);
		textarea.style.height = `${newHeight}px`;
	}, [maxHeight]);

	const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
		if (event.key === "Enter" && !event.shiftKey && onEnter && !isComposing) {
			event.preventDefault();
			onEnter(event);
		}
	};

	return (
		<Textarea
			ref={textareaRef as React.RefObject<HTMLTextAreaElement>}
			{...props}
			onKeyDown={handleKeyDown}
			onCompositionStart={() => setIsComposing(true)}
			onCompositionEnd={() => setIsComposing(false)}
			className={cn(
				"p-0 min-h-[40px] h-[40px] rounded-none resize-none border-none overflow-y-auto shadow-none focus:ring-0 focus:ring-offset-0 focus-visible:ring-0 focus-visible:ring-offset-0",
				className,
			)}
			placeholder={placeholder}
			style={{ maxHeight }}
		/>
	);
};

export default AutoResizeTextarea;
