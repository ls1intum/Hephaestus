import { type QueryClient, useQuery } from "@tanstack/react-query";
import {
	createRootRouteWithContext,
	HeadContent,
	Link,
	Outlet,
	useLocation,
	useMatches,
	useNavigate,
	useRouter,
} from "@tanstack/react-router";
import type React from "react";
import {
	getIntegrationCatalogOptions,
	getUserSettingsOptions,
	listThreadsOptions,
} from "@/api/@tanstack/react-query.gen";
import { ImpersonationBanner } from "@/components/auth/ImpersonationBanner";
import { CookieConsentBanner } from "@/components/consent/CookieConsentBanner";
import Footer from "@/components/core/Footer";
import Header from "@/components/core/Header";
import { StandardPageSurface } from "@/components/core/StandardPageSurface";
import { AppSidebar, type SidebarContext } from "@/components/core/sidebar/AppSidebar";
import { Chat } from "@/components/mentor/Chat";
import { Copilot } from "@/components/mentor/Copilot";
import { defaultPartRenderers } from "@/components/mentor/renderers";
import { PostHogSurveyWidget } from "@/components/surveys/posthog-survey-widget";
import { SidebarInset, SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { Toaster } from "@/components/ui/sonner";
import environment from "@/environment";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useMentorChat } from "@/hooks/use-mentor-chat";
import { useWorkspaceAccess } from "@/hooks/use-workspace-access";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { type AuthContextType, useAuth } from "@/integrations/auth/AuthContext";
import { FeatureFlagDevTools, useFeatureFlag } from "@/integrations/feature-flags";
import { isPosthogEnabled } from "@/integrations/posthog/config";
import { isCopilotExcludedRoute } from "@/lib/copilot-route";
import { getProviderSlug } from "@/lib/provider";

interface MyRouterContext {
	queryClient: QueryClient;
	auth: AuthContextType | undefined;
}

declare module "@tanstack/react-router" {
	interface StaticDataRouteOption {
		surface?: "standard" | "bleed" | "fullscreen" | "auth";
	}
}

/**
 * Named rather than an inline `component: () => …`, so the hooks below sit in something React
 * and its lint rules can both recognise as a component.
 */
function RootLayout() {
	const { pathname } = useLocation();
	const surface = useMatches({
		select: (matches) => {
			for (let index = matches.length - 1; index >= 0; index -= 1) {
				const matchSurface = matches[index]?.staticData.surface;
				if (matchSurface) return matchSurface;
			}
			return "standard";
		},
	});
	const { isAuthenticated, isLoading } = useAuth();
	const { enabled: hasMentorAccess } = useFeatureFlag("MENTOR_ACCESS");
	const { data: userSettings, isError: userSettingsError } = useQuery({
		...getUserSettingsOptions({}),
		enabled: isAuthenticated && isPosthogEnabled,
		retry: 1,
	});
	const allowSurveys =
		isPosthogEnabled && !userSettingsError && (userSettings?.participateInResearch ?? true);
	const showCopilot =
		!isLoading && isAuthenticated && hasMentorAccess && !isCopilotExcludedRoute(pathname);

	if (surface === "auth") {
		return (
			<>
				<HeadContent />
				<CookieConsentBanner />
				<ProviderColorScope>
					<main>
						<Outlet />
					</main>
				</ProviderColorScope>
				<Toaster />
			</>
		);
	}

	return (
		<>
			<HeadContent />
			{/* Rendered early so keyboard/AT users reach the consent region before the app chrome. */}
			<CookieConsentBanner />
			<ImpersonationBanner />
			<ProviderColorScope>
				<SidebarProvider>
					<AppSidebarContainer />
					<SidebarInset
						className="min-w-0"
						style={{ marginRight: "var(--right-sidebar-width, 0)" }}
					>
						<HeaderContainer />
						<div className="flex min-h-0 flex-1 flex-col">
							{surface === "standard" ? (
								<StandardPageSurface className="flex-1">
									<Outlet />
								</StandardPageSurface>
							) : (
								<div
									className={
										surface === "fullscreen" ? "flex min-h-0 min-w-0 flex-1 flex-col" : "flex-1"
									}
								>
									<Outlet />
								</div>
							)}
							{surface !== "fullscreen" && (
								<Footer
									buildInfo={environment.buildInfo}
									isProduction={environment.deployment.isProduction}
								/>
							)}
						</div>
					</SidebarInset>
				</SidebarProvider>
			</ProviderColorScope>
			<Toaster />
			{showCopilot && <GlobalCopilot />}
			{!isLoading && isAuthenticated && allowSurveys && <PostHogSurveyWidget />}
			<FeatureFlagDevTools />
		</>
	);
}

export const Route = createRootRouteWithContext<MyRouterContext>()({
	// Fallback tab title; the deepest match that sets its own `head` wins.
	head: () => ({ meta: [{ title: "Hephaestus" }] }),
	component: RootLayout,
	notFoundComponent: () => (
		<div className="mx-auto flex w-full max-w-2xl flex-col items-center justify-center py-16 text-center">
			<h2 className="text-3xl font-bold mb-4">Page Not Found</h2>
			<p className="text-muted-foreground mb-8">
				The page you're looking for doesn't exist or you don't have permission to view it.
			</p>
			<Link to="/" className="text-primary hover:underline font-medium">
				Return to Home
			</Link>
		</div>
	),
});

function GlobalCopilot() {
	const mentorChat = useMentorChat({
		onError: (error: Error) => {
			console.error("Copilot chat error:", error);
		},
	});

	const router = useRouter();
	const { isAuthenticated, isLoading } = useAuth();
	const { enabled: hasMentorAccess } = useFeatureFlag("MENTOR_ACCESS");
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const { features, isLoading: featuresLoading } = useWorkspaceFeatures(workspaceSlug);

	const handleMessageSubmit = ({ text }: { text: string }) => {
		if (!text.trim()) return;
		mentorChat.sendMessage(text);
	};

	const handleVote = (messageId: string, isUpvote: boolean) => {
		mentorChat.voteMessage(messageId, isUpvote);
	};

	const handleMessageEdit = (messageId: string, content: string) => {
		const messageIndex = mentorChat.messages.findIndex((message) => message.id === messageId);
		if (messageIndex === -1) return;
		mentorChat.setMessages(mentorChat.messages.slice(0, messageIndex));
		mentorChat.sendMessage(content);
	};

	const handleCopy = (content: string) => {
		navigator.clipboard.writeText(content).catch((error: unknown) => {
			console.error("Failed to copy to clipboard:", error);
		});
	};

	if (
		isLoading ||
		featuresLoading ||
		!isAuthenticated ||
		!workspaceSlug ||
		!hasMentorAccess ||
		!features?.mentorEnabled
	) {
		return null;
	}

	return (
		<Copilot
			hasMessages={(mentorChat.messages?.length ?? 0) > 0}
			onNewChat={() => {
				mentorChat.setMessages([]);
			}}
			onOpenFullChat={() => {
				const threadId = mentorChat.currentThreadId || mentorChat.id;
				if (threadId && workspaceSlug) {
					void router.navigate({
						to: "/w/$workspaceSlug/mentor/$threadId",
						params: { threadId, workspaceSlug },
					});
				}
			}}
		>
			<Chat
				messages={mentorChat.messages}
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
				disableAttachments
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

	const effectiveUsername = workspaceUserLogin ?? username;
	const effectiveName =
		workspaceUserName ?? (userProfile && `${userProfile.firstName} ${userProfile.lastName}`);

	return (
		<Header
			sidebarTrigger={isAuthenticated && <SidebarTrigger className="-ml-1" />}
			version={environment.version}
			environmentName={environment.deployment.name}
			isProduction={environment.deployment.isProduction}
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
	const integrationCatalogQuery = useQuery({
		...getIntegrationCatalogOptions({ path: { workspaceSlug: workspaceSlug ?? "" } }),
		enabled: workspaceAccess.isAdmin && Boolean(workspaceSlug),
		placeholderData: (previousData) => previousData,
	});
	const integrationCatalog = Array.isArray(integrationCatalogQuery.data)
		? integrationCatalogQuery.data
		: [];
	const integrationKinds = [
		...new Set([
			...integrationCatalog.map((entry) => entry.kind),
			...(activeWorkspace?.providerType === "GITLAB"
				? (["GITLAB"] as const)
				: activeWorkspace?.providerType === "GITHUB"
					? (["GITHUB"] as const)
					: []),
		]),
	];

	const sidebarContext: SidebarContext = pathname.startsWith("/admin")
		? "admin"
		: pathname === "/mentor" || /^\/w\/[^/]+\/mentor/.test(pathname)
			? "mentor"
			: "main";

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
		void navigate({ href: target, replace: true });
	};

	const handleAddWorkspace = () => {
		void navigate({ to: "/workspaces/new" });
	};

	return (
		<AppSidebar
			username={username}
			isAdmin={workspaceAccess.isAdmin}
			isAppAdmin={isAppAdmin}
			hasMentorAccess={hasMentorAccess}
			integrationKinds={integrationKinds}
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
