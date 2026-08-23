import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { InfoIcon } from "lucide-react";
import { toast } from "sonner";
import {
	autonomyRollupOptions,
	getPracticeReviewSettingsOptions,
	getRepositoriesToMonitorOptions,
	getWorkspaceOptions,
	listAgentsOptions,
	listBackfillRunsOptions,
	listBackfillRunsQueryKey,
	listMembersOptions,
	listPracticesOptions,
	listSweepSchedulesOptions,
	preflightBackfillRunMutation,
	previewCoverageMutation,
	updateBackfillRunStatusMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	CreateReviewBackfillRunRequest,
	UpdatePracticeReviewSettingsRequest,
} from "@/api/types.gen";
import { PracticeReviewBackfill } from "@/components/admin/practices/PracticeReviewBackfill";
import {
	type PracticeReviewField,
	PracticeReviewSettings,
	type PracticeReviewWorkspaceUpdate,
} from "@/components/admin/practices/PracticeReviewSettings";
import { PracticeReviewSweepSchedule } from "@/components/admin/practices/PracticeReviewSweepSchedule";
import {
	PracticeDefinitionSkeleton,
	ReviewSettingsSkeleton,
} from "@/components/admin/practices/PracticeSkeletons";
import { PracticeAutonomyPage } from "@/components/admin/practices/practice-autonomy/PracticeAutonomyPage";
import { ReviewPage } from "@/components/admin/practices/review/ReviewPage";
import type { ReviewRunningState } from "@/components/admin/practices/review/review-readiness";
import {
	DEFAULT_REVIEW_SECTION,
	reviewSearchSchema,
} from "@/components/admin/practices/review/review-sections";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import environment from "@/environment";
import { usePracticeAutonomyMutations } from "@/hooks/use-practice-autonomy-mutations";
import { usePracticeReviewSettingsMutation } from "@/hooks/use-practice-review-settings";
import { useSweepScheduleMutations } from "@/hooks/use-sweep-schedule-mutations";
import { useUpdateWorkspaceFeatures } from "@/hooks/use-update-workspace-features";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/review")({
	head: workspaceAdminHead("Review"),
	validateSearch: reviewSearchSchema,
	component: ReviewRoute,
});

/**
 * The container for the whole review surface. Each tab's data is fetched by its own section function
 * below, and those functions are handed to the page as *elements*: creating an element runs nothing,
 * and Base UI mounts only the open panel, so opening the page costs one workspace read and one
 * agent-binding read — never all three sections' queries.
 */
function ReviewRoute() {
	const { workspaceSlug } = Route.useParams();
	const { section, overrides } = Route.useSearch();
	const navigate = useNavigate({ from: Route.fullPath });

	// Read by the banner above the tabs, so they are the only two requests every visit pays for.
	// Both are shared with the sections on the same keys, so a section's toggle corrects the banner
	// without either of them asking again.
	const workspaceQuery = useQuery({ ...getWorkspaceOptions({ path: { workspaceSlug } }) });
	const bindingsQuery = useQuery({ ...listAgentsOptions({ path: { workspaceSlug } }) });

	const running: ReviewRunningState | undefined = workspaceQuery.data && {
		enabled: workspaceQuery.data.practicesEnabled,
		model: {
			binding: bindingsQuery.data?.find((agent) => agent.purpose === "PRACTICE_REVIEW"),
			isLoading: bindingsQuery.isLoading,
			isError: bindingsQuery.isError,
		},
	};

	return (
		<ReviewPage
			section={section ?? DEFAULT_REVIEW_SECTION}
			// The default section is left out of the URL rather than written into it, so the sidebar's
			// link and a tab click on the first section produce the same address.
			onSectionChange={(next) =>
				void navigate({
					search: (previous) => ({
						...previous,
						section: next === DEFAULT_REVIEW_SECTION ? undefined : next,
					}),
				})
			}
			running={running}
			sections={{
				"how-much": (
					<HowMuchSection
						workspaceSlug={workspaceSlug}
						overridesOnly={overrides === true}
						onOverridesOnlyChange={(next) =>
							void navigate({
								search: (previous) => ({ ...previous, overrides: next ? true : undefined }),
							})
						}
					/>
				),
				"when-and-where": <WhenAndWhereSection workspaceSlug={workspaceSlug} />,
				"past-work": <PastWorkSection workspaceSlug={workspaceSlug} />,
			}}
		/>
	);
}

interface HowMuchSectionProps {
	workspaceSlug: string;
	/** The "only what was set by hand" filter, which lives in the URL so it can be linked to. */
	overridesOnly: boolean;
	onOverridesOnlyChange: (next: boolean) => void;
}

function HowMuchSection({
	workspaceSlug,
	overridesOnly,
	onOverridesOnlyChange,
}: HowMuchSectionProps) {
	const settingsQuery = useQuery({
		...getPracticeReviewSettingsOptions({ path: { workspaceSlug } }),
	});
	const rollupQuery = useQuery({ ...autonomyRollupOptions({ path: { workspaceSlug } }) });
	const practicesQuery = useQuery({ ...listPracticesOptions({ path: { workspaceSlug } }) });

	const autonomyMutations = usePracticeAutonomyMutations(workspaceSlug);
	const updateSettings = usePracticeReviewSettingsMutation(workspaceSlug, {
		success: "Review settings updated",
		error: "Failed to update review settings",
	});

	if (settingsQuery.isPending || rollupQuery.isPending || practicesQuery.isPending) {
		return <PracticeDefinitionSkeleton />;
	}

	const error = settingsQuery.error ?? rollupQuery.error ?? practicesQuery.error;
	if (error || !settingsQuery.data || !rollupQuery.data || !practicesQuery.data) {
		return (
			<QueryErrorAlert
				error={error}
				title="Couldn't load the autonomy settings"
				onRetry={() => {
					void settingsQuery.refetch();
					void rollupQuery.refetch();
					void practicesQuery.refetch();
				}}
			/>
		);
	}

	return (
		<PracticeAutonomyPage
			workspaceSlug={workspaceSlug}
			settings={settingsQuery.data}
			rollup={rollupQuery.data}
			practices={practicesQuery.data}
			pending={{
				workspace: updateSettings.isPending,
				areaSlugs: autonomyMutations.pendingAreaSlugs,
				practiceSlugs: autonomyMutations.pendingPracticeSlugs,
				bulk: autonomyMutations.bulk,
			}}
			overridesOnly={overridesOnly}
			onOverridesOnlyChange={onOverridesOnlyChange}
			onSetWorkspaceDefault={(defaultAutonomy) =>
				updateSettings.mutate({ path: { workspaceSlug }, body: { defaultAutonomy } })
			}
			onClearWorkspaceDefault={() =>
				updateSettings.mutate({
					path: { workspaceSlug },
					body: { reset: ["DEFAULT_AUTONOMY"] },
				})
			}
			onSetAreaAutonomy={(areaSlug, autonomy) =>
				autonomyMutations.setAreaAutonomy.mutate({
					path: { workspaceSlug, areaSlug },
					body: { autonomy },
				})
			}
			onClearAreaAutonomy={(areaSlug) =>
				autonomyMutations.setAreaAutonomy.mutate({ path: { workspaceSlug, areaSlug }, body: {} })
			}
			onSetPracticeAutonomy={(practiceSlug, autonomy) =>
				autonomyMutations.setPracticeAutonomy.mutate({
					path: { workspaceSlug, practiceSlug },
					body: { autonomy },
				})
			}
			onClearPracticeAutonomy={(practiceSlug) =>
				autonomyMutations.setPracticeAutonomy.mutate({
					path: { workspaceSlug, practiceSlug },
					body: {},
				})
			}
			onBulkSetAutonomy={(practiceSlugs, autonomy) => {
				void autonomyMutations.setManyPracticeAutonomies(practiceSlugs, autonomy);
			}}
		/>
	);
}

function WhenAndWhereSection({ workspaceSlug }: { workspaceSlug: string }) {
	const reviewSettingsQuery = useQuery({
		...getPracticeReviewSettingsOptions({ path: { workspaceSlug } }),
	});
	const bindingsQuery = useQuery({ ...listAgentsOptions({ path: { workspaceSlug } }) });
	const workspaceQuery = useQuery({ ...getWorkspaceOptions({ path: { workspaceSlug } }) });
	const schedulesQuery = useQuery(listSweepSchedulesOptions({ path: { workspaceSlug } }));
	const repositoriesQuery = useQuery(getRepositoriesToMonitorOptions({ path: { workspaceSlug } }));
	const membersQuery = useQuery(listMembersOptions({ path: { workspaceSlug } }));
	const coveragePreview = useMutation(previewCoverageMutation());

	const updatePracticeReviewSettings = usePracticeReviewSettingsMutation(workspaceSlug, {
		success: "Review settings updated",
		error: "Failed to update review settings",
	});
	const updateFeatures = useUpdateWorkspaceFeatures(workspaceSlug, {
		success: "Practice review settings updated",
		error: "Failed to update practice review settings",
	});
	const schedules = useSweepScheduleMutations(workspaceSlug);

	// The bindings query is deliberately not part of this gate: its state is passed into the form,
	// which offers the way to change the model, so blocking on it would hide every working setting
	// behind one slow request.
	const isLoading = reviewSettingsQuery.isPending || workspaceQuery.isPending;
	const error = reviewSettingsQuery.error ?? workspaceQuery.error;

	// `space-y-8`: the schedule reads as one more section of the settings above it, so it sits at the
	// same rhythm as the sections inside them rather than looking like a separate panel.
	return (
		<div className="max-w-3xl space-y-8">
			{environment.deployment.environment === "preview" && (
				<Alert>
					<InfoIcon aria-hidden />
					<AlertTitle>This preview starts in silence mode</AlertTitle>
					<AlertDescription>
						Staging data is available, but cloned model bindings, triggers, and recurring checks
						start paused. To test a review in this preview only, select and enable the practice
						review model below, then enable the manual or automatic trigger you need.
					</AlertDescription>
				</Alert>
			)}
			{isLoading ? (
				<ReviewSettingsSkeleton />
			) : error || !reviewSettingsQuery.data || !workspaceQuery.data ? (
				<QueryErrorAlert
					error={error}
					title="Couldn't load the review settings"
					onRetry={() => {
						void reviewSettingsQuery.refetch();
						void workspaceQuery.refetch();
					}}
				/>
			) : (
				<PracticeReviewSettings
					workspaceSlug={workspaceSlug}
					model={{
						binding: bindingsQuery.data?.find((binding) => binding.purpose === "PRACTICE_REVIEW"),
						isLoading: bindingsQuery.isLoading,
						isError: bindingsQuery.isError,
						onRetry: () => void bindingsQuery.refetch(),
					}}
					workspace={{
						enabled: workspaceQuery.data.practicesEnabled,
						autoTriggerEnabled: workspaceQuery.data.practiceReviewAutoTriggerEnabled,
						manualTriggerEnabled: workspaceQuery.data.practiceReviewManualTriggerEnabled,
						isSaving: updateFeatures.isPending,
						onUpdate: (settings: PracticeReviewWorkspaceUpdate) =>
							updateFeatures.mutate({ path: { workspaceSlug }, body: settings }),
					}}
					policy={{
						settings: reviewSettingsQuery.data,
						isSaving: updatePracticeReviewSettings.isPending,
						onUpdate: (settings: UpdatePracticeReviewSettingsRequest) =>
							updatePracticeReviewSettings.mutate({ path: { workspaceSlug }, body: settings }),
						onReset: (field: PracticeReviewField) =>
							updatePracticeReviewSettings.mutate({
								path: { workspaceSlug },
								body: { reset: [field] },
							}),
					}}
					coverage={{
						preview: {
							data: coveragePreview.data,
							isPending: coveragePreview.isPending,
							isError: coveragePreview.isError,
							onPreview: (scope) => {
								coveragePreview.reset();
								coveragePreview.mutate({ path: { workspaceSlug }, body: scope });
							},
						},
						repositories: {
							options: (repositoriesQuery.data ?? []).map((repository) => ({
								value: repository,
								label: repository,
							})),
							isLoading: repositoriesQuery.isLoading,
							isError: repositoriesQuery.isError,
						},
						people: {
							options: (membersQuery.data ?? [])
								.filter((member) => member.userId != null)
								.map((member) => ({
									value: member.userId as number,
									label: member.userName || member.userLogin || `Member ${member.userId}`,
									description: member.userLogin ? `@${member.userLogin}` : undefined,
								})),
							isLoading: membersQuery.isLoading,
							isError: membersQuery.isError,
						},
					}}
				/>
			)}
			{/* Outside the gate above, and deliberately: the recurring check is a separate resource with
			    its own request and its own error handling, and it is a standing authorisation to spend —
			    so a failed review-settings load must not be what stops an admin pausing a runaway one. */}
			<PracticeReviewSweepSchedule
				schedules={schedulesQuery.data ?? []}
				isLoading={schedulesQuery.isLoading}
				isError={schedulesQuery.isError}
				onRetry={() => void schedulesQuery.refetch()}
				isSaving={schedules.isSaving}
				onCreate={schedules.onCreate}
				onReplace={schedules.onReplace}
				onDelete={schedules.onDelete}
			/>
		</div>
	);
}

/** A backfill can run for hours, so the list re-reads itself while one is under way. */
const ACTIVE_BACKFILL_POLL_MS = 15_000;

function PastWorkSection({ workspaceSlug }: { workspaceSlug: string }) {
	const queryClient = useQueryClient();

	const runsQuery = useQuery({
		...listBackfillRunsOptions({ path: { workspaceSlug } }),
		refetchInterval: (query) =>
			query.state.data?.some((run) => run.status === "RUNNING" || run.status === "PAUSED")
				? ACTIVE_BACKFILL_POLL_MS
				: false,
	});

	const invalidate = () =>
		queryClient.invalidateQueries({
			queryKey: listBackfillRunsQueryKey({ path: { workspaceSlug } }),
		});

	const preflight = useMutation({
		...preflightBackfillRunMutation(),
		onSuccess: () => {
			void invalidate();
		},
		onError: (error) => {
			toast.error("Couldn't estimate this backfill", { description: problemDetailOf(error) });
		},
	});

	const updateStatus = useMutation({
		...updateBackfillRunStatusMutation(),
		onSuccess: (run) => {
			void invalidate();
			if (run.status === "RUNNING") {
				toast.success("Backfill started");
			} else if (run.status === "CANCELLED") {
				toast.success("Backfill stopped");
			}
		},
		onError: (error) => {
			toast.error("Couldn't update this backfill", { description: problemDetailOf(error) });
		},
	});

	return (
		<div className="max-w-3xl">
			<PracticeReviewBackfill
				runs={runsQuery.data ?? []}
				isLoading={runsQuery.isLoading}
				isError={runsQuery.isError}
				onRetry={() => void runsQuery.refetch()}
				isEstimating={preflight.isPending}
				onEstimate={(request: CreateReviewBackfillRunRequest) =>
					preflight.mutate({ path: { workspaceSlug }, body: request })
				}
				isUpdating={updateStatus.isPending}
				onConfirm={(runId) =>
					updateStatus.mutate({ path: { workspaceSlug, runId }, body: { status: "RUNNING" } })
				}
				onCancel={(runId) =>
					updateStatus.mutate({ path: { workspaceSlug, runId }, body: { status: "CANCELLED" } })
				}
			/>
		</div>
	);
}
