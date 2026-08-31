import type { Meta, StoryObj } from "@storybook/react";

import { HephMark, HephaestusLogo, HephaestusWordmark } from "./HephaestusLogo";

const meta = {
	component: HephaestusLogo,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof HephaestusLogo>;

export default meta;
type Story = StoryObj<typeof meta>;

export const LightAndDark: Story = {
	render: () => (
		<div className="grid overflow-hidden rounded-2xl border sm:grid-cols-2">
			<div className="flex min-h-48 items-center bg-white p-10 text-[#17191f]">
				<HephaestusLogo markClassName="size-12" wordmarkClassName="text-3xl" />
			</div>
			<div className="dark flex min-h-48 items-center bg-[#111318] p-10 text-white">
				<HephaestusLogo markClassName="size-12" wordmarkClassName="text-3xl" />
			</div>
		</div>
	),
};

export const PlatformFit: Story = {
	render: () => (
		<div className="flex flex-wrap items-end gap-8">
			{[
				{ label: "Slack mobile · 36px", size: 36, shape: "rounded-lg" },
				{ label: "GitHub profile · circle", size: 96, shape: "rounded-full" },
				{ label: "App icon · squircle", size: 96, shape: "rounded-[22%]" },
			].map(({ label, size, shape }) => (
				<div key={label} className="text-center">
					<div className={`mx-auto overflow-hidden ${shape}`} style={{ width: size, height: size }}>
						<HephMark className="size-full" />
					</div>
					<p className="mt-3 text-xs text-muted-foreground">{label}</p>
				</div>
			))}
		</div>
	),
};

export const MarkScale: Story = {
	render: () => (
		<div className="flex items-end gap-6">
			{[16, 24, 32, 48, 64].map((size) => (
				<div key={size} className="text-center">
					<span style={{ width: size, height: size }} className="mx-auto block">
						<HephMark className="size-full" />
					</span>
					<p className="mt-2 text-xs text-muted-foreground">{size}px</p>
				</div>
			))}
		</div>
	),
};

export const Wordmark: Story = {
	render: () => <HephaestusWordmark className="text-4xl font-semibold tracking-tight" />,
};
