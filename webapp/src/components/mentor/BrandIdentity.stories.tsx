import type { Meta, StoryObj } from "@storybook/react";
import type { CSSProperties, ReactNode } from "react";

import { MentorIcon } from "./MentorIcon";

const meta: Meta = {
	title: "components/mentor/Brand identity exploration",
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"A review board for replacing the hammer with Heph. These are deliberately prototypes, not exported brand assets. Compare silhouette, color, wordmark treatment, and real-world scale before choosing a direction.",
			},
		},
	},
};

export default meta;
type Story = StoryObj<typeof meta>;

const colors = [
	{ name: "Signal blue", value: "#315FDC", note: "Closest to the existing mentor color" },
	{ name: "Forge violet", value: "#6555C6", note: "Distinctive without feeling playful" },
	{ name: "Ember", value: "#C4512D", note: "A quiet reference to the forge" },
	{ name: "Molten amber", value: "#A65F00", note: "Warm, but lower contrast on white" },
] as const;

function Surface({ children, dark = false }: { children: ReactNode; dark?: boolean }) {
	return (
		<div className={dark ? "dark bg-[#111318] text-white" : "bg-white text-[#17191f]"}>
			{children}
		</div>
	);
}

function Mark({
	color,
	treatment,
}: {
	color: string;
	treatment: "bare" | "solid" | "soft" | "ring";
}) {
	if (treatment === "bare") {
		return <MentorIcon className="shrink-0" size={56} pad={3} animated={false} />;
	}

	const style = { "--candidate-color": color } as CSSProperties;
	const treatments = {
		solid: "bg-(--candidate-color) text-white",
		soft: "bg-(--candidate-color)/12 text-(--candidate-color)",
		ring: "border-2 border-(--candidate-color) text-(--candidate-color)",
	};

	return (
		<span
			style={style}
			className={`inline-flex size-16 shrink-0 items-center justify-center rounded-full ${treatments[treatment]}`}
		>
			<MentorIcon size={45} pad={3} animated={false} />
		</span>
	);
}

function Wordmark({
	color,
	treatment,
}: {
	color: string;
	treatment: "bar" | "dot" | "heph" | "plain";
}) {
	const style = { color };
	const base = "text-3xl font-semibold tracking-tight";
	if (treatment === "bar") {
		return (
			<span className={base}>
				Heph<span style={style}>|</span>aestus
			</span>
		);
	}
	if (treatment === "dot") {
		return (
			<span className={base}>
				Heph<span style={style}>·</span>aestus
			</span>
		);
	}
	if (treatment === "heph") {
		return (
			<span className={base}>
				<span style={style}>Heph</span>aestus
			</span>
		);
	}
	return <span className={base}>Hephaestus</span>;
}

export const MarkDirections: Story = {
	render: () => (
		<div className="grid min-h-screen grid-cols-1 lg:grid-cols-2">
			{[false, true].map((dark) => (
				<Surface key={String(dark)} dark={dark}>
					<div className="p-8 lg:p-12">
						<p className="mb-8 text-sm font-medium text-current/60">
							{dark ? "Dark surface" : "Light surface"}
						</p>
						<div className="grid gap-8 sm:grid-cols-2">
							{(["bare", "solid", "soft", "ring"] as const).map((treatment) => (
								<div key={treatment} className="flex items-center gap-4">
									<Mark color="#315FDC" treatment={treatment} />
									<div>
										<p className="font-semibold capitalize">{treatment}</p>
										<p className="text-sm text-current/60">
											{treatment === "solid"
												? "Strongest favicon and avatar"
												: "Quieter application mark"}
										</p>
									</div>
								</div>
							))}
						</div>
					</div>
				</Surface>
			))}
		</div>
	),
};

export const ColorDirections: Story = {
	render: () => (
		<div className="min-h-screen bg-background p-8 text-foreground lg:p-12">
			<div className="mx-auto max-w-5xl">
				<h2 className="text-2xl font-semibold">Candidate brand colors</h2>
				<p className="mt-2 max-w-2xl text-muted-foreground">
					The circle is the proposed primary mark: a stable accent field with the white Heph drawing
					in negative. The plain drawing remains available where a circle would be visually heavy.
				</p>
				<div className="mt-10 grid gap-6 sm:grid-cols-2">
					{colors.map((color) => (
						<div key={color.name} className="flex items-center gap-5 rounded-2xl border p-5">
							<Mark color={color.value} treatment="solid" />
							<div className="min-w-0">
								<p className="font-semibold">{color.name}</p>
								<p className="font-mono text-sm text-muted-foreground">{color.value}</p>
								<p className="mt-1 text-sm text-muted-foreground">{color.note}</p>
							</div>
						</div>
					))}
				</div>
			</div>
		</div>
	),
};

export const WordmarkDirections: Story = {
	render: () => (
		<div className="grid min-h-screen grid-cols-1 lg:grid-cols-2">
			{[false, true].map((dark) => (
				<Surface key={String(dark)} dark={dark}>
					<div className="space-y-10 p-8 lg:p-12">
						<p className="text-sm font-medium text-current/60">{dark ? "Dark" : "Light"}</p>
						{(["plain", "bar", "dot", "heph"] as const).map((treatment) => (
							<div key={treatment} className="flex items-center gap-4">
								<Mark color="#315FDC" treatment="solid" />
								<div>
									<Wordmark color={dark ? "#8EAEFF" : "#315FDC"} treatment={treatment} />
									<p className="mt-1 text-sm capitalize text-current/55">{treatment} accent</p>
								</div>
							</div>
						))}
					</div>
				</Surface>
			))}
		</div>
	),
};

export const ScaleAndContext: Story = {
	render: () => (
		<div className="min-h-screen bg-background p-8 text-foreground lg:p-12">
			<div className="mx-auto max-w-5xl space-y-12">
				<section>
					<h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
						Icon scale
					</h2>
					<div className="mt-5 flex items-end gap-6">
						{[16, 24, 32, 48, 64].map((size) => (
							<div key={size} className="text-center">
								<span
									className="inline-flex items-center justify-center rounded-full bg-[#315FDC] text-white"
									style={{ width: size, height: size }}
								>
									<MentorIcon
										size={size * 0.72}
										pad={3}
										strokeWidth={size < 24 ? 2.4 : 2}
										animated={false}
									/>
								</span>
								<p className="mt-2 text-xs text-muted-foreground">{size}px</p>
							</div>
						))}
					</div>
				</section>
				<section className="overflow-hidden rounded-2xl border">
					<div className="flex h-16 items-center gap-3 border-b px-5">
						<Mark color="#315FDC" treatment="solid" />
						<Wordmark color="#315FDC" treatment="bar" />
					</div>
					<div className="grid gap-6 p-6 md:grid-cols-3">
						<div className="rounded-xl border p-5">
							<Mark color="#315FDC" treatment="solid" />
							<p className="mt-4 font-semibold">App icon / favicon</p>
						</div>
						<div className="rounded-xl bg-muted p-5">
							<Mark color="#315FDC" treatment="bare" />
							<p className="mt-4 font-semibold">In-product mentor</p>
						</div>
						<div className="rounded-xl bg-[#315FDC] p-5 text-white">
							<MentorIcon size={56} pad={3} animated={false} />
							<p className="mt-4 font-semibold">Social field</p>
						</div>
					</div>
				</section>
			</div>
		</div>
	),
};
