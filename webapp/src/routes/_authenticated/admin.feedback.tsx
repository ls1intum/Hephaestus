import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { toast } from "sonner";

import {
	adminCreateProductSurveyMutation,
	adminListProductFeedbackOptions,
	adminListProductSurveyResponsesOptions,
	adminListProductSurveysOptions,
	adminListProductSurveysQueryKey,
	adminListWorkspacesOptions,
} from "@/api/@tanstack/react-query.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { TablePagination } from "@/components/common/TablePagination";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";

const QUESTION_TYPES = [
	{ label: "Free text", value: "TEXT" },
	{ label: "Single choice", value: "SINGLE_CHOICE" },
	{ label: "Rating (1–5)", value: "RATING" },
] as const;

export const Route = createFileRoute("/_authenticated/admin/feedback")({
	component: AdminProductFeedbackPage,
});

function AdminProductFeedbackPage() {
	const queryClient = useQueryClient();
	const [title, setTitle] = useState("");
	const [description, setDescription] = useState("");
	const [prompt, setPrompt] = useState("");
	const [workspaceId, setWorkspaceId] = useState("all");
	const [questionType, setQuestionType] = useState<"TEXT" | "SINGLE_CHOICE" | "RATING">("TEXT");
	const [choiceOptions, setChoiceOptions] = useState("");
	const [feedbackPage, setFeedbackPage] = useState(0);
	const [responsePage, setResponsePage] = useState(0);
	const [surveyPage, setSurveyPage] = useState(0);
	const feedback = useQuery(
		adminListProductFeedbackOptions({ query: { page: feedbackPage, size: 20 } }),
	);
	const surveys = useQuery(
		adminListProductSurveysOptions({ query: { page: surveyPage, size: 20 } }),
	);
	const responses = useQuery(
		adminListProductSurveyResponsesOptions({ query: { page: responsePage, size: 20 } }),
	);
	const feedbackItems = feedback.data?.content ?? [];
	const surveyItems = surveys.data?.content ?? [];
	const responseItems = responses.data?.content ?? [];
	const workspaces = useQuery(adminListWorkspacesOptions());
	const workspaceOptions = [
		{ label: "All workspaces", value: "all" },
		...(workspaces.data?.map((workspace) => ({
			label: workspace.displayName,
			value: String(workspace.id),
		})) ?? []),
	];
	const options = choiceOptions
		.split("\n")
		.map((option) => option.trim())
		.filter(Boolean);
	const hasDuplicateOptions = new Set(options).size !== options.length;
	const create = useMutation({
		...adminCreateProductSurveyMutation(),
		onSuccess: () => {
			toast.success("Survey published.");
			setTitle("");
			setDescription("");
			setPrompt("");
			setWorkspaceId("all");
			setQuestionType("TEXT");
			setChoiceOptions("");
			void queryClient.invalidateQueries({
				queryKey: adminListProductSurveysQueryKey({ query: { page: surveyPage, size: 20 } }),
			});
		},
		onError: () => toast.error("Couldn't publish the survey."),
	});
	return (
		<div className="mx-auto w-full max-w-6xl space-y-6">
			<div>
				<h1 className="text-3xl font-bold">Product feedback</h1>
				<p className="text-muted-foreground">
					First-party survey authoring and the instance feedback inbox.
				</p>
			</div>
			<Tabs defaultValue="inbox">
				<TabsList>
					<TabsTrigger value="inbox">Feedback</TabsTrigger>
					<TabsTrigger value="responses">Survey responses</TabsTrigger>
					<TabsTrigger value="surveys">Surveys</TabsTrigger>
				</TabsList>
				<TabsContent value="inbox" className="space-y-3" aria-busy={feedback.isLoading}>
					{feedback.isError ? (
						<QueryErrorAlert
							error={feedback.error}
							title="Feedback couldn't be loaded"
							onRetry={() => void feedback.refetch()}
						/>
					) : null}
					{feedbackItems.map((item) => (
						<Card key={item.id}>
							<CardHeader>
								<CardTitle className="text-base">
									{item.kind === "BUG" ? "Bug report" : "Feedback"}
								</CardTitle>
								<CardDescription>
									{item.createdAt?.toLocaleString() ?? "Unknown time"} · account {item.accountId}
									{item.workspaceId ? ` · workspace ${item.workspaceId}` : ""}
								</CardDescription>
							</CardHeader>
							<CardContent>
								<p className="whitespace-pre-wrap">{item.message}</p>
								{item.pagePath ? (
									<p className="mt-2 text-sm text-muted-foreground">Page: {item.pagePath}</p>
								) : null}
							</CardContent>
						</Card>
					))}
					{feedback.isLoading ? <p>Loading…</p> : null}
					{feedback.isSuccess && feedbackItems.length === 0 ? <p>No feedback yet.</p> : null}
					<TablePagination
						page={feedbackPage}
						totalPages={feedback.data?.page?.totalPages ?? 0}
						onPageChange={setFeedbackPage}
					/>
				</TabsContent>
				<TabsContent value="responses" className="space-y-3" aria-busy={responses.isLoading}>
					{responses.isError ? (
						<QueryErrorAlert
							error={responses.error}
							title="Survey responses couldn't be loaded"
							onRetry={() => void responses.refetch()}
						/>
					) : null}
					{responseItems.map((item) => (
						<Card key={item.id}>
							<CardHeader>
								<CardTitle className="text-base">
									{item.disposition === "DISMISSED" ? "Dismissed" : "Response"}
								</CardTitle>
								<CardDescription>
									{item.createdAt?.toLocaleString() ?? "Unknown time"} · {item.surveyTitle} ·
									account {item.accountId}
								</CardDescription>
							</CardHeader>
							{item.answers ? (
								<CardContent>
									<dl className="space-y-3">
										{item.questions.map((question) => {
											const answer = item.answers?.[question.id];
											return answer ? (
												<div key={question.id}>
													<dt className="font-medium">{question.prompt}</dt>
													<dd className="whitespace-pre-wrap text-sm">{answer}</dd>
												</div>
											) : null;
										})}
									</dl>
								</CardContent>
							) : null}
						</Card>
					))}
					{responses.isLoading ? <p>Loading…</p> : null}
					{responses.isSuccess && responseItems.length === 0 ? (
						<p>No survey responses yet.</p>
					) : null}
					<TablePagination
						page={responsePage}
						totalPages={responses.data?.page?.totalPages ?? 0}
						onPageChange={setResponsePage}
					/>
				</TabsContent>
				<TabsContent value="surveys" className="space-y-6" aria-busy={surveys.isLoading}>
					{surveys.isError ? (
						<QueryErrorAlert
							error={surveys.error}
							title="Surveys couldn't be loaded"
							onRetry={() => void surveys.refetch()}
						/>
					) : null}
					{workspaces.isError ? (
						<QueryErrorAlert
							error={workspaces.error}
							title="Workspace audiences couldn't be loaded"
							onRetry={() => void workspaces.refetch()}
						/>
					) : null}
					<Card>
						<CardHeader>
							<CardTitle>Publish a survey</CardTitle>
							<CardDescription>
								Choose an audience and answer type for a focused one-question survey.
							</CardDescription>
						</CardHeader>
						<CardContent>
							<form
								className="space-y-4"
								onSubmit={(event) => {
									event.preventDefault();
									create.mutate({
										body: {
											title: title.trim(),
											description: description.trim(),
											workspaceId: workspaceId === "all" ? undefined : Number(workspaceId),
											startsAt: new Date(),
											questions: [
												{
													id: "response",
													prompt: prompt.trim(),
													type: questionType,
													options: questionType === "SINGLE_CHOICE" ? options : [],
													required: true,
												},
											],
										},
									});
								}}
							>
								<div>
									<Label htmlFor="survey-title">Title</Label>
									<Input
										id="survey-title"
										name="title"
										required
										value={title}
										maxLength={160}
										onChange={(e) => setTitle(e.target.value)}
									/>
								</div>
								<div>
									<Label htmlFor="survey-description">Purpose</Label>
									<Textarea
										id="survey-description"
										name="description"
										required
										value={description}
										maxLength={500}
										onChange={(e) => setDescription(e.target.value)}
									/>
								</div>
								<div>
									<Label htmlFor="survey-prompt">Question</Label>
									<Textarea
										id="survey-prompt"
										name="prompt"
										required
										value={prompt}
										maxLength={300}
										onChange={(e) => setPrompt(e.target.value)}
									/>
								</div>
								<div className="space-y-2">
									<Label htmlFor="survey-type">Answer type</Label>
									<Select
										items={QUESTION_TYPES}
										value={questionType}
										onValueChange={(value) => value && setQuestionType(value)}
									>
										<SelectTrigger id="survey-type">
											<SelectValue />
										</SelectTrigger>
										<SelectContent aria-labelledby="survey-type">
											<SelectItem value="TEXT">Free text</SelectItem>
											<SelectItem value="SINGLE_CHOICE">Single choice</SelectItem>
											<SelectItem value="RATING">Rating (1–5)</SelectItem>
										</SelectContent>
									</Select>
								</div>
								{questionType === "SINGLE_CHOICE" ? (
									<div>
										<Label htmlFor="survey-options">Choices (one per line)</Label>
										<Textarea
											id="survey-options"
											name="options"
											required
											value={choiceOptions}
											maxLength={4000}
											onChange={(e) => setChoiceOptions(e.target.value)}
										/>
									</div>
								) : null}
								<div className="space-y-2">
									<Label htmlFor="survey-workspace">Audience</Label>
									<Select
										items={workspaceOptions}
										value={workspaceId}
										onValueChange={(value) => value && setWorkspaceId(value)}
									>
										<SelectTrigger id="survey-workspace">
											<SelectValue />
										</SelectTrigger>
										<SelectContent aria-labelledby="survey-workspace">
											<SelectItem value="all">All workspaces</SelectItem>
											{workspaces.data?.map((workspace) => (
												<SelectItem key={workspace.id} value={String(workspace.id)}>
													{workspace.displayName}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
								</div>
								<Button
									type="submit"
									disabled={
										!title.trim() ||
										!description.trim() ||
										!prompt.trim() ||
										(questionType === "SINGLE_CHOICE" && options.length < 2) ||
										hasDuplicateOptions ||
										workspaces.isError ||
										create.isPending
									}
								>
									{create.isPending ? "Publishing…" : "Publish survey"}
								</Button>
							</form>
						</CardContent>
					</Card>
					{surveyItems.map((survey) => (
						<Card key={survey.id}>
							<CardHeader>
								<CardTitle className="text-base">{survey.title}</CardTitle>
								<CardDescription>
									{survey.workspaceId ? `Workspace ${survey.workspaceId}` : "All workspaces"} ·{" "}
									{survey.createdAt?.toLocaleString() ?? "Unknown time"}
								</CardDescription>
							</CardHeader>
							<CardContent>{survey.description}</CardContent>
						</Card>
					))}
					{surveys.isLoading ? <p>Loading…</p> : null}
					{surveys.isSuccess && surveyItems.length === 0 ? <p>No surveys yet.</p> : null}
					<TablePagination
						page={surveyPage}
						totalPages={surveys.data?.page?.totalPages ?? 0}
						onPageChange={setSurveyPage}
					/>
				</TabsContent>
			</Tabs>
		</div>
	);
}
