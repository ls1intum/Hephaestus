/**
 * Workspace error state components.
 *
 * All components follow accessibility best practices:
 * - Use appropriate ARIA roles (alert, status) for screen reader announcements
 * - Auto-focus for keyboard navigation
 * - Icons marked aria-hidden to avoid redundant announcements
 * - Clear action buttons with descriptive labels
 */

export { NoWorkspace } from "./NoWorkspace";
export { WorkspaceError, type WorkspaceErrorProps } from "./WorkspaceError";
export {
	WorkspaceForbidden,
	type WorkspaceForbiddenProps,
} from "./WorkspaceForbidden";
export {
	WorkspaceNotFound,
	type WorkspaceNotFoundProps,
} from "./WorkspaceNotFound";
