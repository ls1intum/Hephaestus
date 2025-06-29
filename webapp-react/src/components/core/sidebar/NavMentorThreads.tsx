import {
	SidebarGroup,
	SidebarGroupContent,
	SidebarGroupLabel,
	SidebarMenu,
	SidebarMenuButton,
	SidebarMenuItem,
} from "@/components/ui/sidebar";
import { Link } from "@tanstack/react-router";

// Mock data for chat threads
const MOCK_THREADS = {
	today: [
		{
			id: "1",
			title: "React Hooks Best Practices",
			lastMessage: "Thanks for the useState explanation!",
			timestamp: "2 min ago",
			isActive: false,
			hasUnread: false,
		},
		{
			id: "2",
			title: "TypeScript Generic Types",
			lastMessage: "Can you show me more examples?",
			timestamp: "1 hour ago",
			isActive: true,
			hasUnread: false,
		},
		{
			id: "3",
			title: "Database Design Help",
			lastMessage: "What about normalization?",
			timestamp: "3 hours ago",
			isActive: false,
			hasUnread: true,
		},
	],
	yesterday: [
		{
			id: "4",
			title: "API Architecture Review",
			lastMessage: "REST vs GraphQL comparison",
			timestamp: "1 day ago",
			isActive: false,
			hasUnread: false,
		},
		{
			id: "5",
			title: "Frontend Performance Tips",
			lastMessage: "Lazy loading images",
			timestamp: "1 day ago",
			isActive: false,
			hasUnread: true,
		},
		{
			id: "6",
			title: "CSS Grid vs Flexbox",
			lastMessage: "When to use each?",
			timestamp: "1 day ago",
			isActive: false,
			hasUnread: false,
		},
		{
			id: "7",
			title: "JavaScript ES2023 Features",
			lastMessage: "New array methods",
			timestamp: "1 day ago",
			isActive: false,
			hasUnread: false,
		},
	],
	last7Days: [
		{
			id: "5",
			title: "Performance Optimization",
			lastMessage: "Bundle size analysis tips",
			timestamp: "2 days ago",
			isActive: false,
			hasUnread: false,
		},
	],
	last30Days: [
		{
			id: "6",
			title: "Testing Strategies",
			lastMessage: "Unit vs integration tests",
			timestamp: "1 week ago",
			isActive: false,
			hasUnread: false,
		},
		{
			id: "7",
			title: "DevOps Best Practices",
			lastMessage: "CI/CD pipeline setup",
			timestamp: "2 weeks ago",
			isActive: false,
			hasUnread: false,
		},
		{
			id: "8",
			title: "Frontend Frameworks Comparison",
			lastMessage: "React vs Vue vs Angular",
			timestamp: "3 weeks ago",
			isActive: false,
			hasUnread: false,
		},
		{
			id: "9",
			title: "State Management Solutions",
			lastMessage: "Redux vs MobX vs Zustand",
			timestamp: "4 weeks ago",
			isActive: false,
			hasUnread: false,
		},
	],
};

/**
 * Navigation component showing chat thread history in mentor mode.
 */
export function NavMentorThreads() {
	return (
		<>
			<ThreadGroup title="Today" threads={MOCK_THREADS.today} />
			<ThreadGroup title="Yesterday" threads={MOCK_THREADS.yesterday} />
			<ThreadGroup title="Last 7 Days" threads={MOCK_THREADS.last7Days} />
			<ThreadGroup title="Last 30 Days" threads={MOCK_THREADS.last30Days} />
		</>
	);
}

function ThreadGroup({
	title,
	threads,
}: { title: string; threads: typeof MOCK_THREADS.today }) {
	return (
		<SidebarGroup>
			<SidebarGroupLabel>{title}</SidebarGroupLabel>
			<SidebarGroupContent>
				<SidebarMenu>
					{threads.map((thread) => (
						<SidebarMenuItem key={thread.id}>
							<SidebarMenuButton asChild isActive={thread.isActive}>
								<Link to="/mentor" search={{ threadId: thread.id }}>
									{thread.title}
								</Link>
							</SidebarMenuButton>
						</SidebarMenuItem>
					))}
				</SidebarMenu>
			</SidebarGroupContent>
		</SidebarGroup>
	);
}
