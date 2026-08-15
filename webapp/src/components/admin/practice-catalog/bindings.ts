import type {
	PracticeAutomatedReviewPolicy,
	PracticeBinding,
	PracticeDefinitionOptions,
	PracticeEvidenceRequirement,
	PracticeWorkTypeDefinitionOptions,
} from "@/api/types.gen";
import { ARTIFACT_KIND, ARTIFACT_KIND_VALUES } from "@/lib/artifact-kinds";

export type EvidenceStance = PracticeEvidenceRequirement["stance"];
export type EvidenceRole = "NOT_USED" | EvidenceStance;

/**
 * The kind of work a signal is about, by the same rule the server uses: everything before the last
 * dot. `scm.pull_request.merged` is about a `scm.pull_request`.
 */
export function artifactKindOfSignal(signal: string): string {
	const lastDot = signal.lastIndexOf(".");
	return lastDot === -1 ? signal : signal.slice(0, lastDot);
}

/**
 * Reads the first signal only. A practice is reviewed on one occasion, so there is nothing else to
 * read; a stored practice whose bindings disagree is no longer representable server-side either.
 */
export function artifactKindOfBindings(bindings: readonly PracticeBinding[]): string | undefined {
	const signal = bindings.find((binding) => binding.signals.length > 0)?.signals[0];
	return signal ? artifactKindOfSignal(signal) : undefined;
}

/** An occasion with nothing chosen yet: a strip the author can tick, rather than no strip at all. */
export const EMPTY_BINDING: PracticeBinding = { signals: [], needs: [] };

/**
 * The one occasion a practice is reviewed on, read out of the list the wire carries. A stored
 * practice holding more than one — only writable before the server settled on one — is read as its
 * first, which is what saving it again leaves behind.
 */
export function soleBinding(bindings: readonly PracticeBinding[]): PracticeBinding {
	return bindings[0] ?? EMPTY_BINDING;
}

/**
 * A binding in the shape the server stores it. Not cosmetic: the unsaved-changes guard compares the
 * edited value against the loaded one by deep equality, and the server rewrites both lists on the way
 * in, so without normalising here an untouched practice would come back looking edited.
 */
export function normalizeBinding(binding: PracticeBinding): PracticeBinding {
	return {
		signals: [...new Set(binding.signals)].sort((left, right) => left.localeCompare(right)),
		needs: [...binding.needs].sort((left, right) =>
			left.sourceKind.localeCompare(right.sourceKind),
		),
		...(binding.onDrafts ? { onDrafts: true } : {}),
	};
}

export function roleOf(
	needs: readonly PracticeEvidenceRequirement[],
	sourceKind: string,
): EvidenceRole {
	return needs.find((need) => need.sourceKind === sourceKind)?.stance ?? "NOT_USED";
}

export function withRole(
	needs: readonly PracticeEvidenceRequirement[],
	sourceKind: string,
	role: EvidenceRole,
): PracticeEvidenceRequirement[] {
	const remaining = needs.filter((need) => need.sourceKind !== sourceKind);
	if (role !== "NOT_USED") remaining.push({ sourceKind, stance: role });
	return remaining.sort((left, right) => left.sourceKind.localeCompare(right.sourceKind));
}

/** The occasion a practice on this work type starts out on: its recommended moments and evidence. */
export function recommendedBinding(options: PracticeWorkTypeDefinitionOptions): PracticeBinding {
	const recommended = options.signals.filter((option) => option.recommended);
	const signals = (recommended.length > 0 ? recommended : options.signals.slice(0, 1)).map(
		(option) => option.signal,
	);
	return normalizeBinding({ signals, needs: options.recommendedNeeds });
}

export function workTypeOptionsFor(
	definitionOptions: PracticeDefinitionOptions,
	artifactKind: string | undefined,
): PracticeWorkTypeDefinitionOptions | undefined {
	return definitionOptions.workTypes.find((option) => option.artifactKind === artifactKind);
}

/**
 * The server sorts work types alphabetically as a stability guarantee, not a claim about what an
 * author most often writes. That claim is presentation: known kinds lead, unknown ones follow rather
 * than disappearing.
 */
export function orderedWorkTypes(
	definitionOptions: PracticeDefinitionOptions,
): PracticeWorkTypeDefinitionOptions[] {
	const rank = new Map(ARTIFACT_KIND_VALUES.map((kind, index) => [kind as string, index]));
	return [...definitionOptions.workTypes].sort(
		(left, right) =>
			(rank.get(left.artifactKind) ?? ARTIFACT_KIND_VALUES.length) -
			(rank.get(right.artifactKind) ?? ARTIFACT_KIND_VALUES.length),
	);
}

/** Only a pull request can be a draft; on any other kind the choice is about an impossible state. */
export function hasDrafts(artifactKind: string | undefined): boolean {
	return artifactKind === ARTIFACT_KIND.pullRequest;
}

/** A practice has one occasion, so its controls are named rather than numbered. */
export const OCCASION_ID_PREFIX = "practice-occasion";

export function occasionFieldId(field: string): string {
	return `${OCCASION_ID_PREFIX}-${field}`;
}

export interface BindingsProblem {
	message: string;
	focusId: string;
}

/**
 * Mirrors the server's refusals rather than paraphrasing them. An empty signal list and a mixed-kind
 * binding never come back as a readable 400: they are rejected inside the `PracticeBinding` record
 * constructor during deserialization, which surfaces as a generic 500, so the form is the only place
 * they can be explained.
 */
export function bindingsProblem(
	binding: PracticeBinding,
	policy: PracticeAutomatedReviewPolicy,
	options: PracticeWorkTypeDefinitionOptions | undefined,
): BindingsProblem | undefined {
	if (binding.signals.length === 0) {
		return {
			message: "Choose when this practice is reviewed.",
			focusId: occasionFieldId("signals"),
		};
	}
	const declared = new Set(options?.signals.map((option) => option.signal));
	const noAutomatedReview = policy.automatedReview.mode === "NONE";
	for (const signal of binding.signals) {
		if (declared.size > 0 && !declared.has(signal)) {
			return {
				message: "One of the chosen moments does not apply to this kind of work.",
				focusId: occasionFieldId("signals"),
			};
		}
	}
	if (noAutomatedReview && binding.needs.length > 0) {
		return {
			message: "Guidance only cannot read any evidence.",
			focusId: occasionFieldId("evidence"),
		};
	}
	if (!noAutomatedReview && !binding.needs.some((need) => need.stance !== "CONTEXTUAL")) {
		return {
			message: "This review needs at least one source it cannot run without.",
			focusId: occasionFieldId("evidence"),
		};
	}
	const exhaustiveBlocked = binding.needs.find(
		(need) =>
			need.stance === "EXHAUSTIVE" &&
			options?.allowedSources.some(
				(source) => source.sourceKind === need.sourceKind && !source.supportsExhaustiveEvidence,
			),
	);
	if (exhaustiveBlocked) {
		return {
			message:
				"One source can never be captured whole, so nothing this review says about what is absent from it can rest on it.",
			focusId: occasionFieldId("evidence"),
		};
	}
	return undefined;
}
