import { useQuery } from "@tanstack/react-query";
import { AlertCircle, ScanEye } from "lucide-react";
import { getWorkspaceOptions, listAgentsOptions } from "@/api/@tanstack/react-query.gen";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ReviewHowMuchSection } from "./ReviewHowMuchSection";
import { ReviewPastWorkSection } from "./ReviewPastWorkSection";
import { ReviewWhenAndWhereSection } from "./ReviewWhenAndWhereSection";
import { type ReviewRunningTone, reviewRunningSummary } from "./review-readiness";
import { REVIEW_SECTIONS, type ReviewSectionId } from "./review-sections";

export interface ReviewPageProps {
	workspaceSlug: string;
	section: ReviewSectionId;
	onSectionChange: (section: ReviewSectionId) => void;
	/** The autonomy screen's "only what was set by hand" filter, kept in the URL. */
	overridesOnly: boolean;
	onOverridesOnlyChange: (next: boolean) => void;
}

/**
 * How review behaves in this workspace, in one place.
 *
 * <p>Three sidebar entries before this — autonomy, settings, and past work — which meant an admin
 * asking one question ("why did this not get reviewed?") had to visit three destinations that never
 * announced they were related. They are one page and three sections now, because they are three
 * decisions and not one.
 *
 * <p>Tabs, not a stack: the sections are alternatives, and each is long. It also buys the thing the
 * stack could not — an inactive Base UI panel is not rendered, so a section's queries do not run
 * until somebody opens it, and the autonomy screen's sticky summary strip cannot hang over the
 * settings below it.
 */
export function ReviewPage({
	workspaceSlug,
	section,
	onSectionChange,
	overridesOnly,
	onOverridesOnlyChange,
}: ReviewPageProps) {
	const active = REVIEW_SECTIONS.find((candidate) => candidate.id === section);

	return (
		<PageLayout>
			<PageHeader icon={<ScanEye />} title="Review" description={active?.description} />
			<ReviewRunningBanner workspaceSlug={workspaceSlug} />
			<Tabs
				value={section}
				onValueChange={(next) => onSectionChange(next as ReviewSectionId)}
				className="gap-6"
			>
				{/* `h-auto` and wrapping: three labels do not fit on one 320px line, and a tab list that
				    overflows drags the whole page sideways rather than scrolling. */}
				<TabsList className="h-auto w-full flex-wrap justify-start sm:w-fit">
					{REVIEW_SECTIONS.map((candidate) => (
						<TabsTrigger key={candidate.id} value={candidate.id} className="h-8 flex-none px-3">
							{candidate.label}
						</TabsTrigger>
					))}
				</TabsList>
				<TabsContent value="how-much">
					<ReviewHowMuchSection
						workspaceSlug={workspaceSlug}
						overridesOnly={overridesOnly}
						onOverridesOnlyChange={onOverridesOnlyChange}
					/>
				</TabsContent>
				<TabsContent value="when-and-where">
					<ReviewWhenAndWhereSection workspaceSlug={workspaceSlug} />
				</TabsContent>
				<TabsContent value="past-work">
					<ReviewPastWorkSection workspaceSlug={workspaceSlug} />
				</TabsContent>
			</Tabs>
		</PageLayout>
	);
}

/**
 * The two states that stop reviews are the two the alert is allowed to colour. "Running" and
 * "still checking" are not warnings, and a page whose header is always tinted teaches an admin to
 * stop reading it.
 */
const TONE_VARIANTS: Record<ReviewRunningTone, "default" | "warning"> = {
	running: "default",
	unknown: "default",
	blocked: "warning",
	off: "warning",
};

/**
 * The one fact all three sections need, in the header they share.
 *
 * <p>Every section below is a set of controls that does nothing at all on a workspace where practice
 * reviews are switched off or no model is bound — and each of them looks like it is working. An admin
 * can spend a long time on target branches and autonomy tiers before finding that out.
 *
 * <p>`role="status"`, not `alert`: this is the standing state of the page rather than a response to
 * anything the reader just did, and an assertive announcement on every visit would interrupt them
 * mid-sentence.
 */
function ReviewRunningBanner({ workspaceSlug }: { workspaceSlug: string }) {
	// Both queries are shared with the sections, on the same keys, so this costs no extra request —
	// and the header stays correct the moment a section's toggle writes to either of them.
	const workspaceQuery = useQuery({ ...getWorkspaceOptions({ path: { workspaceSlug } }) });
	const bindingsQuery = useQuery({ ...listAgentsOptions({ path: { workspaceSlug } }) });

	if (workspaceQuery.isPending || !workspaceQuery.data) return null;

	const { tone, sentence } = reviewRunningSummary({
		enabled: workspaceQuery.data.practicesEnabled,
		model: {
			binding: bindingsQuery.data?.find((agent) => agent.purpose === "PRACTICE_REVIEW"),
			isLoading: bindingsQuery.isLoading,
			isError: bindingsQuery.isError,
		},
	});

	return (
		<Alert variant={TONE_VARIANTS[tone]} role="status">
			{tone === "blocked" || tone === "off" ? <AlertCircle /> : null}
			<AlertDescription>{sentence}</AlertDescription>
		</Alert>
	);
}
