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
	/** The "only what was set by hand" filter, which lives in the URL so it can be linked to. */
	overridesOnly: boolean;
	onOverridesOnlyChange: (next: boolean) => void;
}

/**
 * Tabs, not a stack: an inactive Base UI panel is not rendered, so a section's queries do not run
 * until somebody opens it, and the autonomy section's sticky summary strip cannot hang over another
 * section's settings.
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
				{/* `h-auto` and wrapping: the labels do not fit on one narrow line, and a tab list that
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

/** Only the states that stop reviews are coloured: a header that is always tinted stops being read. */
const TONE_VARIANTS: Record<ReviewRunningTone, "default" | "warning"> = {
	running: "default",
	unknown: "default",
	blocked: "warning",
	off: "warning",
};

/**
 * `role="status"`, not `alert`: this is the standing state of the page rather than a response to
 * anything the reader just did, and an assertive announcement on every visit would interrupt them
 * mid-sentence.
 */
function ReviewRunningBanner({ workspaceSlug }: { workspaceSlug: string }) {
	// Both queries are shared with the sections, on the same keys, so this costs no extra request and
	// stays correct the moment a section's toggle writes to either of them.
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
