import { create } from "zustand";

/**
 * In-memory (NON-persisted) flag for impersonation write-mode.
 *
 * Impersonation is read-only by default; the server's {@code ImpersonationGuard} rejects any
 * state-changing request during impersonation unless it carries `X-Impersonation-Allow-Writes: true`.
 * When the operator explicitly enables writes (a second confirmation in {@code ImpersonationBanner}),
 * this flag flips on and the request interceptor in `main.tsx` attaches that header.
 *
 * It is intentionally NOT persisted: each impersonation session — and each page reload — starts back
 * in read-only mode, so writes are always a deliberate, fresh opt-in rather than a sticky setting.
 */
interface ImpersonationState {
	/** Whether the operator has opted into making changes as the impersonated user. */
	writesEnabled: boolean;
	setWritesEnabled: (enabled: boolean) => void;
}

export const useImpersonationStore = create<ImpersonationState>((set) => ({
	writesEnabled: false,
	setWritesEnabled: (writesEnabled) => set({ writesEnabled }),
}));
