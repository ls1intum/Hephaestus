import { useCallback, useRef, useState } from "react";

type ScrollBehavior = "auto" | "smooth";

export function useScrollToBottom() {
	const containerRef = useRef<HTMLDivElement>(null);
	const endRef = useRef<HTMLDivElement>(null);

	// Simple local state for UI concerns
	const [isAtBottom, setIsAtBottom] = useState(false);

	// Auto-scroll to bottom with specified behavior
	const scrollToBottom = useCallback((behavior: ScrollBehavior = "smooth") => {
		endRef.current?.scrollIntoView({ behavior });
	}, []);

	// Viewport intersection callbacks
	const onViewportEnter = useCallback(() => {
		setIsAtBottom(true);
	}, []);

	const onViewportLeave = useCallback(() => {
		setIsAtBottom(false);
	}, []);

	return {
		containerRef,
		endRef,
		isAtBottom,
		scrollToBottom,
		onViewportEnter,
		onViewportLeave,
	};
}
