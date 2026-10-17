import { USER_VIEW_STORAGE_KEY, type UserViewSession } from "@/runtime/user-view/session";

/** Operator 42 is the MSW fixture's signed-in account, so this view is bound to it by default. */
export const storedUserView: UserViewSession = {
	operatorAccountId: 42,
	workspaceSlug: "engineering",
	workspaceName: "Engineering",
	accountId: "11",
	providerLogin: "alex",
	name: "Alex",
	unclaimed: true,
	reason: "Check practice feedback",
};

/** Leaves a view in the tab the way `startUserView` does, without the navigation jsdom cannot perform. */
export function storeUserView(overrides: Partial<UserViewSession> = {}): void {
	sessionStorage.setItem(
		USER_VIEW_STORAGE_KEY,
		JSON.stringify({ ...storedUserView, ...overrides }),
	);
}
