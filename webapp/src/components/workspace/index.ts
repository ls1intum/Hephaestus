/**
 * Workspace error state components.
 *
 * All components follow WCAG 2.2 accessibility best practices:
 * - Use appropriate ARIA roles (alert, status) for screen reader announcements
 * - aria-live for dynamic content announcement (assertive/polite)
 * - aria-atomic ensures entire message is read as a unit
 * - Auto-focus via requestAnimationFrame for keyboard navigation
 * - Icons marked aria-hidden to prevent redundant announcements
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
