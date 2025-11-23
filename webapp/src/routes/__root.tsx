import type { QueryClient } from "@tanstack/react-query";
import { useQuery } from "@tanstack/react-query";
import {
	createRootRouteWithContext,
	Link,
	Outlet,
	useLocation,
	useNavigate,
	useRouter,
} from "@tanstack/react-router";
import { TanStackRouterDevtools } from "@tanstack/react-router-devtools";
import { Toaster } from "sonner";
import {
	getGroupedThreadsOptions,
	getUserSettingsOptions,
} from "@/api/@tanstack/react-query.gen";
import Footer from "@/components/core/Footer";
import Header from "@/components/core/Header";
import {
	AppSidebar,
	type SidebarContext,
} from "@/components/core/sidebar/AppSidebar";
import { ArtifactOverlayContainer } from "@/components/mentor/ArtifactOverlayContainer";
import { Chat } from "@/components/mentor/Chat";
import { Copilot } from "@/components/mentor/Copilot";
import { defaultPartRenderers } from "@/components/mentor/renderers";
import { PostHogSurveyWidget } from "@/components/surveys/posthog-survey-widget";
import {
	SidebarInset,
	SidebarProvider,
	SidebarTrigger,
} from "@/components/ui/sidebar";
import environment from "@/environment";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceAccess } from "@/hooks/use-workspace-access";
import { useMentorChat } from "@/hooks/useMentorChat";
import { type AuthContextType, useAuth } from "@/integrations/auth/AuthContext";
import { isPosthogEnabled } from "@/integrations/posthog/config";
import { useTheme } from "@/integrations/theme";
import type { ChatMessage } from "@/lib/types";
import TanstackQueryLayout from "../integrations/tanstack-query/layout";

interface MyRouterContext {
	queryClient: QueryClient;
	auth: AuthContextType | undefined;
}

export const Route = createRootRouteWithContext<MyRouterContext>()({
	component: () => {
		const { theme } = useTheme();
		const { pathname } = useLocation();
		const { isAuthenticated, hasRole, isLoading } = useAuth();
		const { data: userSettings, isError: userSettingsError } = useQuery({
			...getUserSettingsOptions({}),
			enabled: isAuthenticated && isPosthogEnabled,
			retry: 1,
		});
		const allowSurveys =
			isPosthogEnabled &&
			!userSettingsError &&
			(userSettings?.participateInResearch ?? true);
		const isMentorRoute =
			pathname === "/mentor" || /^\/w\/[^/]+\/mentor/.test(pathname);

		// Exclude routes where Copilot should not appear
		const isExcludedRoute =
			isMentorRoute ||
			pathname.startsWith("/admin") ||
			pathname.startsWith("/settings") ||
			pathname.startsWith("/legal") ||
			pathname === "/imprint" ||
			pathname === "/privacy";

		const showCopilot =
			!isLoading &&
			isAuthenticated &&
			hasRole("mentor_access") &&
			!isExcludedRoute;

		return (
			<>
				<SidebarProvider>
					<AppSidebarContainer />
					<SidebarInset>
						<HeaderContainer />
						<div className="min-h-[calc(100dvh-4rem)] flex flex-col">
							<main className={`${isMentorRoute ? "" : "p-4"}`}>
								<Outlet />
							</main>
							{!isMentorRoute && (
								<div className="flex justify-end flex-col h-full">
									<Footer />
								</div>
							)}
						</div>
					</SidebarInset>
				</SidebarProvider>
				<Toaster theme={theme} />
				<TanStackRouterDevtools />
				<TanstackQueryLayout />

				{showCopilot && <GlobalCopilot />}
				{!isLoading && isAuthenticated && allowSurveys && (
					<PostHogSurveyWidget />
				)}
			</>
		);
	},
	// Add notFoundComponent to handle route not found errors
	notFoundComponent: () => (
		<div className="container py-16 flex flex-col items-center justify-center text-center">
			<h2 className="text-3xl font-bold mb-4">Page Not Found</h2>
			<p className="text-muted-foreground mb-8">
				The page you're looking for doesn't exist or you don't have permission
				to view it.
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
	const { isAuthenticated, hasRole, isLoading } = useAuth();
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
		// Send the edited content as a new message; prepareSendMessagesRequest will set previousMessageId to the new last message
		mentorChat.sendMessage(content);
	};

	const handleCopy = (content: string) => {
		navigator.clipboard.writeText(content).catch((error) => {
			console.error("Failed to copy to clipboard:", error);
		});
	};

	if (isLoading || !isAuthenticated || !hasRole("mentor_access")) {
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
				showSuggestedActions={true}
				inputPlaceholder="Ask me anything..."
				disableAttachments={true}
				className="h-full max-h-none"
				partRenderers={defaultPartRenderers}
			/>
			<ArtifactOverlayContainer
				messages={mentorChat.messages as ChatMessage[]}
				votes={mentorChat.votes}
				status={mentorChat.status}
				attachments={[]}
				readonly={false}
				onMessageSubmit={handleMessageSubmit}
				onStop={mentorChat.stop}
				onFileUpload={() => Promise.resolve([])}
				onMessageEdit={handleMessageEdit}
				onCopy={handleCopy}
				onVote={handleVote}
				partRenderers={defaultPartRenderers}
			/>
		</Copilot>
	);
}

function HeaderContainer() {
	const { pathname } = useLocation();
	const { isAuthenticated, isLoading, username, userProfile, login, logout } =
		useAuth();

	return (
		<Header
			sidebarTrigger={
				!(pathname === "/landing" || !isAuthenticated) && (
					<SidebarTrigger className="-ml-1" />
				)
			}
			version={environment.version}
			isAuthenticated={isAuthenticated}
			isLoading={isLoading}
			name={userProfile && `${userProfile.firstName} ${userProfile.lastName}`}
			username={username}
			onLogin={login}
			onLogout={logout}
		/>
	);
}

function AppSidebarContainer() {
	const { pathname } = useLocation();
	const { isAuthenticated, username, hasRole } = useAuth();
	const navigate = useNavigate();
	const workspaceAccess = useWorkspaceAccess();
	const { workspaceSlug, workspaces, selectWorkspace } = workspaceAccess;
	const hasWorkspace = Boolean(workspaceSlug);
	const activeWorkspace = workspaces.find(
		(ws) => ws.workspaceSlug === workspaceSlug,
	);

	const sidebarContext: SidebarContext =
		pathname === "/mentor" || /^\/w\/[^/]+\/mentor/.test(pathname)
			? "mentor"
			: "main";

	// Always call useQuery but only enable when in mentor context and authenticated
	const {
		data: threadGroups,
		isLoading: mentorThreadsLoading,
		error: mentorThreadsError,
	} = useQuery({
		...getGroupedThreadsOptions({
			path: { workspaceSlug: workspaceSlug ?? "" },
		}),
		enabled: sidebarContext === "mentor" && isAuthenticated && hasWorkspace,
	});

	if (pathname === "/landing" || !isAuthenticated || username === undefined) {
		return null;
	}

	const handleWorkspaceChange = (ws: typeof activeWorkspace) => {
		if (!ws) return;
		selectWorkspace(ws.workspaceSlug);
		const remainder = pathname.replace(/^\/w\/[^/]+/, "");
		const target = `/w/${ws.workspaceSlug}${remainder || "/"}`;
		navigate({ to: target as never, replace: true });
	};

	const handleAddWorkspace = () => {
		// TODO: Replace with actual GitHub App installation URL or a proper dialog
		alert(
			"To create a workspace, please install the Hephaestus GitHub App on your repository. Contact your administrator for the installation URL.",
		);
	};

	return (
		<AppSidebar
			username={username}
			isAdmin={workspaceAccess.isAdmin}
			hasMentorAccess={hasRole("mentor_access")}
			context={sidebarContext}
			workspaces={workspaces}
			activeWorkspace={activeWorkspace}
			onWorkspaceChange={handleWorkspaceChange}
			onAddWorkspace={handleAddWorkspace}
			workspacesLoading={workspaceAccess.isLoading}
			mentorThreadGroups={
				sidebarContext === "mentor" ? threadGroups : undefined
			}
			mentorThreadsLoading={
				sidebarContext === "mentor" ? mentorThreadsLoading : undefined
			}
			mentorThreadsError={
				sidebarContext === "mentor" && mentorThreadsError
					? "Failed to load threads"
					: undefined
			}
		/>
	);
}
