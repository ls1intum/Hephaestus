// Augment TanStack Router HistoryState so we can pass custom state on navigation
import "@tanstack/react-router";

import type { WorkspaceSwitchBehavior } from "@/lib/workspace-switching";

declare module "@tanstack/react-router" {
	interface HistoryState {
		autoGreeting?: boolean;
	}

	interface StaticDataRouteOption {
		workspaceSwitch?: WorkspaceSwitchBehavior;
	}
}
