import { type QueryClient, useQuery } from "@tanstack/react-query";
import {
	createRootRouteWithContext,
	Link,
	Outlet,
	useLocation,
	useNavigate,
	useRouter,
} from "@tanstack/react-router";
import type React from "react";
import { Toaster } from "sonner";
import { getUserSettingsOptions, listThreadsOptions } from "@/api/@tanstack/react-query.gen";
import { ImpersonationBanner } from "@/components/auth/ImpersonationBanner";
import { CookieConsentBanner } from "@/components/consent/CookieConsentBanner";
import Footer from "@/components/core/Footer";
import Header from "@/components/core/Header";
import { AppSidebar, type SidebarContext } from "@/components/core/sidebar/AppSidebar";
import { Chat } from "@/components/mentor/Chat";
import { Copilot } from "@/components/mentor/Copilot";
import { defaultPartRenderers } from "@/components/mentor/renderers";
import { PostHogSurveyWidget } from "@/components/surveys/posthog-survey-widget";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import environment from "@/environment";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceAccess } from "@/hooks/use-workspace-access";
import { useMentorChat } from "@/hooks/useMentorChat";
import { type AuthContextType, useAuth } from "@/integrations/auth/AuthContext";
import { FeatureFlagDevTools, useFeatureFlag } from "@/integrations/feature-flags";
import { isPosthogEnabled } from "@/integrations/posthog/config";
import { useTheme } from "@/integrations/theme";
import { getProviderSlug } from "@/lib/provider";
import type { ChatMessage } from "@/lib/types";

interface MyRouterContext {
	queryClient: QueryClient;
	auth: AuthContextType | undefined;
}

export const Route = createRootRouteWithContext<MyRouterContext>()({
	component: () => {
		const { theme } = useTheme();
		const { pathname } = useLocation();
		const { isAuthenticated, isLoading } = useAuth();
		const { enabled: hasMentorAccess } = useFeatureFlag("MENTOR_ACCESS");
		const { data: userSettings, isError: userSettingsError } = useQuery({
			...getUserSettingsOptions({}),
			enabled: isAuthenticated && isPosthogEnabled,
			retry: 1,
		});
		const allowSurveys =
			isPosthogEnabled && !userSettingsError && (userSettings?.participateInResearch ?? true);
		const isMentorRoute = pathname === "/mentor" || /^\/w\/[^/]+\/mentor/.test(pathname);
		const isAchievementsRoute =
			/^\/w\/[^/]+\/achievements/.test(pathname) ||
			/^\/w\/[^/]+\/user\/[^/]+\/achievements/.test(pathname);
		// Routes that use full-height layouts without padding or footer
		const isFullscreenRoute = isMentorRoute || isAchievementsRoute;

		// Exclude routes where Copilot should not appear
		const isExcludedRoute =
			isMentorRoute ||
			pathname.startsWith("/admin") ||
			pathname.startsWith("/settings") ||
			pathname.startsWith("/legal") ||
			pathname === "/imprint" ||
			pathname === "/privacy";

		const showCopilot = !isLoading && isAuthenticated && hasMentorAccess && !isExcludedRoute;

		// Auth screens (/login, /w/<slug>/login, /auth/*) render on a focused, full-viewport canvas with
		// NO app chrome — no header (which otherwise duplicates the sign-in buttons), no footer, no
		// sidebar. The page owns the whole viewport, so it can center cleanly without subtracting header/
		// footer height.
		const isAuthRoute =
			pathname === "/login" ||
			pathname.startsWith("/auth/") ||
			/^\/w\/[^/]+\/login\/?$/.test(pathname);

		if (isAuthRoute) {
			return (
				<>
					<CookieConsentBanner />
					<ProviderColorScope>
						<Outlet />
					</ProviderColorScope>
					<Toaster theme={theme} />
				</>
			);
		}

		return (
			<>
				{/* Rendered early so keyboard/AT users reach the consent region before the app chrome. */}
				<CookieConsentBanner />
				<ImpersonationBanner />
				<ProviderColorScope>
					<SidebarProvider>
						<AppSidebarContainer />
						<SidebarInset style={{ marginRight: "var(--right-sidebar-width, 0)" }}>
							<HeaderContainer />
							<div className="flex min-h-[calc(100dvh-4rem)] flex-col">
								<main className={isFullscreenRoute ? "" : "flex-1 p-4"}>
									<Outlet />
								</main>
								{!isFullscreenRoute && <Footer buildInfo={environment.buildInfo} />}
							</div>
						</SidebarInset>
					</SidebarProvider>
				</ProviderColorScope>
				<Toaster theme={theme} />
				{showCopilot && <GlobalCopilot />}
				{!isLoading && isAuthenticated && allowSurveys && <PostHogSurveyWidget />}
				<FeatureFlagDevTools />
			</>
		);
	},
	// Add notFoundComponent to handle route not found errors
	notFoundComponent: () => (
		<div className="container py-16 flex flex-col items-center justify-center text-center">
			<h2 className="text-3xl font-bold mb-4">Page Not Found</h2>
			<p className="text-muted-foreground mb-8">
				The page you're looking for doesn't exist or you don't have permission to view it.
			</p>
			<Link to="/" className="text-blue-500 hover:underline font-medium">
				Return to Home
			</Link>
		</div>
	),
});

function GlobalCopilot() {
	// Independent chat state for the copilot widget
	const mentorChat = useMentorChat({
		onError: (error: Error) => {
			console.error("Copilot chat error:", error);
		},
	});

	const router = useRouter();
	const { isAuthenticated, isLoading } = useAuth();
	const { enabled: hasMentorAccess } = useFeatureFlag("MENTOR_ACCESS");
	const { workspaceSlug } = useActiveWorkspaceSlug();

	const handleMessageSubmit = ({ text }: { text: string }) => {
		if (!text.trim()) return;
		mentorChat.sendMessage(text);
	};

	const handleVote = (messageId: string, isUpvote: boolean) => {
		mentorChat.voteMessage(messageId, isUpvote);
	};

	// Edit a previous message: discard that message and all following locally, then send the edited content
	const handleMessageEdit = (messageId: string, content: string) => {
		const idx = mentorChat.messages.findIndex((m) => m.id === messageId);
		if (idx === -1) return;
		// Keep everything before the edited message
		mentorChat.setMessages(mentorChat.messages.slice(0, idx));
		// Send the edited content as a new message; the server resolves the parent from the
		// trimmed history (we only ship the new user message, not previousMessageId).
		mentorChat.sendMessage(content);
	};

	const handleCopy = (content: string) => {
		navigator.clipboard.writeText(content).catch((error) => {
			console.error("Failed to copy to clipboard:", error);
		});
	};

	if (isLoading || !isAuthenticated || !hasMentorAccess) {
		return null;
	}

	return (
		<Copilot
			hasMessages={(mentorChat.messages?.length ?? 0) > 0}
			onNewChat={() => {
				// Reset to a fresh session by clearing messages; useMentorChat will keep a new id.
				mentorChat.setMessages([]);
			}}
			onOpenFullChat={() => {
				const threadId = mentorChat.currentThreadId || mentorChat.id;
				if (threadId && workspaceSlug) {
					router.navigate({
						to: "/w/$workspaceSlug/mentor/$threadId",
						params: { threadId, workspaceSlug },
					});
				}
			}}
		>
			<Chat
				id={mentorChat.currentThreadId || mentorChat.id}
				messages={mentorChat.messages as ChatMessage[]}
				votes={mentorChat.votes}
				status={mentorChat.status}
				readonly={false}
				attachments={[]}
				onMessageSubmit={handleMessageSubmit}
				onMessageEdit={handleMessageEdit}
				onStop={mentorChat.stop}
				onFileUpload={() => Promise.resolve([])}
				onAttachmentsChange={() => {}}
				onCopy={handleCopy}
				onVote={handleVote}
				inputPlaceholder="Ask me anything..."
				disableAttachments={true}
				className="h-full max-h-none"
				partRenderers={defaultPartRenderers}
			/>
		</Copilot>
	);
}

function HeaderContainer() {
	const {
		isAuthenticated,
		isLoading,
		username,
		userProfile,
		login,
		logout,
		getUserProfilePictureUrl,
	} = useAuth();
	const {
		workspaceSlug,
		userLogin: workspaceUserLogin,
		userName: workspaceUserName,
	} = useWorkspaceAccess();

	// Inside a workspace, "you" are the account's identity for that workspace's provider (ADR 0017):
	// e.g. a GitLab-logged-in account is its GitHub user in a GitHub workspace. Prefer that identity for
	// the displayed name and the "My Profile" link so it points at the right per-provider profile;
	// fall back to the global account identity outside a workspace (or before membership resolves).
	const effectiveUsername = workspaceUserLogin ?? username;
	const effectiveName =
		workspaceUserName ?? (userProfile && `${userProfile.firstName} ${userProfile.lastName}`);

	return (
		<Header
			sidebarTrigger={isAuthenticated && <SidebarTrigger className="-ml-1" />}
			version={environment.version}
			isAuthenticated={isAuthenticated}
			isLoading={isLoading}
			name={effectiveName}
			username={effectiveUsername}
			avatarUrl={getUserProfilePictureUrl()}
			workspaceSlug={workspaceSlug}
			onLogin={login}
			onLogout={logout}
		/>
	);
}

/**
 * Sets `data-provider` attribute on a wrapper div so provider-aware CSS custom
 * properties (--color-provider-*) resolve to the correct palette (GitHub Primer
 * or GitLab Pajamas) based on the active workspace's provider type.
 */
function ProviderColorScope({ children }: { children: React.ReactNode }) {
	const { providerType } = useActiveWorkspaceSlug();
	return <div data-provider={getProviderSlug(providerType)}>{children}</div>;
}

function AppSidebarContainer() {
	const { pathname } = useLocation();
	const { isAuthenticated, username, isAppAdmin } = useAuth();
	const { enabled: hasMentorAccess } = useFeatureFlag("MENTOR_ACCESS");
	const navigate = useNavigate();
	const workspaceAccess = useWorkspaceAccess();
	const { workspaceSlug, workspaces, selectWorkspace } = workspaceAccess;
	const hasWorkspace = Boolean(workspaceSlug);
	const workspaceList = Array.isArray(workspaces) ? workspaces : [];
	const activeWorkspace = workspaceList.find((ws) => ws.workspaceSlug === workspaceSlug);

	const sidebarContext: SidebarContext = pathname.startsWith("/admin")
		? "admin"
		: pathname === "/mentor" || /^\/w\/[^/]+\/mentor/.test(pathname)
			? "mentor"
			: "main";

	// Always call useQuery but only enable when in mentor context and authenticated
	const {
		data: mentorThreads,
		isLoading: mentorThreadsLoading,
		error: mentorThreadsError,
	} = useQuery({
		...listThreadsOptions({
			path: { workspaceSlug: workspaceSlug ?? "" },
		}),
		enabled: sidebarContext === "mentor" && isAuthenticated && hasWorkspace,
	});

	if (!isAuthenticated || username === undefined) {
		return null;
	}

	const handleWorkspaceChange = (ws: typeof activeWorkspace) => {
		if (!ws) return;
		selectWorkspace(ws.workspaceSlug);
		const remainder = pathname.replace(/^\/w\/[^/]+/, "");
		const target = `/w/${ws.workspaceSlug}${remainder || "/"}`;
		// Runtime-built internal path (slug + preserved subpath): use the typed `href` field
		// rather than `to as never` — relative href stays an SPA navigation.
		navigate({ href: target, replace: true });
	};

	const handleAddWorkspace = () => {
		navigate({ to: "/workspaces/new" });
	};

	return (
		<AppSidebar
			isAdmin={workspaceAccess.isAdmin}
			isAppAdmin={isAppAdmin}
			hasMentorAccess={hasMentorAccess}
			context={sidebarContext}
			workspaces={workspaceList}
			activeWorkspace={activeWorkspace}
			onWorkspaceChange={handleWorkspaceChange}
			onAddWorkspace={handleAddWorkspace}
			workspacesLoading={workspaceAccess.isLoading}
			mentorThreads={sidebarContext === "mentor" ? mentorThreads : undefined}
			mentorThreadsLoading={sidebarContext === "mentor" ? mentorThreadsLoading : undefined}
			mentorThreadsError={
				sidebarContext === "mentor" && mentorThreadsError ? "Failed to load threads" : undefined
			}
		/>
	);
}
