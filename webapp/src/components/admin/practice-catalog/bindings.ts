import type {
	PracticeAutomatedReviewPolicy,
	PracticeBinding,
	PracticeDefinitionOptions,
	PracticeEvidenceRequirement,
	PracticeWorkTypeDefinitionOptions,
} from "@/api/types.gen";
import { lifecycleSignals } from "@/components/admin/practice-catalog/occasion-moments";
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
 * Reads the first signal only. A practice whose bindings disagree is not representable server-side
 * and the form never builds one, so guessing a winner here would only hide the disagreement.
 */
export function artifactKindOfBindings(bindings: readonly PracticeBinding[]): string | undefined {
	const signal = bindings.find((binding) => binding.signals.length > 0)?.signals[0];
	return signal ? artifactKindOfSignal(signal) : undefined;
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

export function normalizeBindings(bindings: readonly PracticeBinding[]): PracticeBinding[] {
	return bindings.map(normalizeBinding);
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

export function recommendedBinding(
	options: PracticeWorkTypeDefinitionOptions,
	usedSignals: readonly string[] = [],
): PracticeBinding {
	const taken = new Set(usedSignals);
	// Only moments on the artifact's lifecycle are candidates. Seeding a new occasion with the
	// hand-asked review would hand the author an occasion that never fires on its own — the server
	// matches a manual request against any binding of the kind, so the signal decides nothing.
	const free = lifecycleSignals(options.signals).filter((option) => !taken.has(option.signal));
	const recommended = free.filter((option) => option.recommended);
	const signals = (recommended.length > 0 ? recommended : free.slice(0, 1)).map(
		(option) => option.signal,
	);
	return normalizeBinding({ signals, needs: options.recommendedNeeds });
}

export function claimedSignals(bindings: readonly PracticeBinding[]): Set<string> {
	return new Set(bindings.flatMap((binding) => binding.signals));
}

/**
 * Which occasion holds each moment, numbered as the author sees them, skipping the occasion being
 * edited. The strip needs the number and not just the fact: "already taken" leaves the author hunting
 * for which card to change.
 */
export function signalOwners(
	bindings: readonly PracticeBinding[],
	exceptIndex: number,
): Map<string, number> {
	const owners = new Map<string, number>();
	bindings.forEach((binding, index) => {
		if (index === exceptIndex) return;
		for (const signal of binding.signals) owners.set(signal, index + 1);
	});
	return owners;
}

/** True once no moment on this kind's lifecycle is left for another occasion to start on. */
export function everyMomentClaimed(
	options: PracticeWorkTypeDefinitionOptions,
	claimed: ReadonlySet<string>,
): boolean {
	return lifecycleSignals(options.signals).every((option) => claimed.has(option.signal));
}

export function workTypeOptionsFor(
	definitionOptions: PracticeDefinitionOptions,
	artifactKind: string | undefined,
): PracticeWorkTypeDefinitionOptions | undefined {
	return definitionOptions.workTypes.find((option) => option.artifactKind === artifactKind);
}

/**
 * The server sorts work types alphabetically, which is a stability guarantee rather than a claim
 * about what an author most often writes. That claim is presentation and lives here: the kinds this
 * build can label lead, and one it has never heard of follows rather than disappearing.
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

/**
 * Only a pull request can be a draft. Offering the choice on any other kind asks the author about a
 * state that cannot occur.
 */
export function hasDrafts(artifactKind: string | undefined): boolean {
	return artifactKind === ARTIFACT_KIND.pullRequest;
}

export function bindingIdPrefix(index: number): string {
	return `practice-binding-${index}`;
}

export function bindingFieldId(index: number, field: string): string {
	return `${bindingIdPrefix(index)}-${field}`;
}

export function belongsToBinding(focusId: string | undefined, index: number): boolean {
	return focusId?.startsWith(`${bindingIdPrefix(index)}-`) ?? false;
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
	bindings: readonly PracticeBinding[],
	policy: PracticeAutomatedReviewPolicy,
	options: PracticeWorkTypeDefinitionOptions | undefined,
): BindingsProblem | undefined {
	if (bindings.length === 0) {
		return { message: "Add at least one occasion that starts a review.", focusId: ADD_BINDING_ID };
	}
	if (bindings.length > MAX_BINDINGS) {
		return {
			message: `A practice can have at most ${MAX_BINDINGS} occasions.`,
			focusId: bindingFieldId(MAX_BINDINGS, "signals"),
		};
	}
	const declared = new Set(options?.signals.map((option) => option.signal));
	const seen = new Set<string>();
	const noAutomatedReview = policy.automatedReview.mode === "NONE";
	for (const [index, binding] of bindings.entries()) {
		if (binding.signals.length === 0) {
			return {
				message: "Choose when this occasion starts a review.",
				focusId: bindingFieldId(index, "signals"),
			};
		}
		for (const signal of binding.signals) {
			if (declared.size > 0 && !declared.has(signal)) {
				return {
					message: "One of the chosen moments does not apply to this kind of work.",
					focusId: bindingFieldId(index, "signals"),
				};
			}
			if (seen.has(signal)) {
				return {
					message: "Two occasions start on the same moment. Merge them or change one.",
					focusId: bindingFieldId(index, "signals"),
				};
			}
			seen.add(signal);
		}
		if (noAutomatedReview && binding.needs.length > 0) {
			return {
				message: "Guidance only cannot read any evidence.",
				focusId: bindingFieldId(index, "evidence"),
			};
		}
		if (!noAutomatedReview && !binding.needs.some((need) => need.stance !== "CONTEXTUAL")) {
			return {
				message: "Every occasion needs at least one source the review cannot run without.",
				focusId: bindingFieldId(index, "evidence"),
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
					"One source can never be captured whole, so nothing this review says about what is missing from it can rest on it.",
				focusId: bindingFieldId(index, "evidence"),
			};
		}
	}
	return undefined;
}

export const MAX_BINDINGS = 10;
export const ADD_BINDING_ID = "practice-add-binding";
