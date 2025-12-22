// Augment TanStack Router HistoryState so we can pass custom state on navigation
import "@tanstack/react-router";

declare module "@tanstack/react-router" {
	interface HistoryState {
		autoGreeting?: boolean;
	}
}
