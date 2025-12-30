import { useNavigate } from "@tanstack/react-router";

/**
 * Hook that provides a handler function to navigate to the home page.
 * Shared by error state components (WorkspaceForbidden, WorkspaceNotFound).
 */
export function useGoHome() {
	const navigate = useNavigate();

	return () => {
		navigate({ to: "/" });
	};
}
