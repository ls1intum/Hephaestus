import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { fn } from "storybook/test";
import { VersionFooter } from "./VersionFooter";

/**
 * VersionFooter component displays version navigation controls for document histories.
 * Provides restore functionality and navigation between document versions.
 */
const meta = {
	component: VersionFooter,
	parameters: {
		layout: "fullscreen",
	},
	tags: ["autodocs"],
	argTypes: {
		currentVersionIndex: {
			description: "Current version index being viewed",
			control: "number",
		},
		isRestoring: {
			description: "Whether restore operation is in progress",
			control: "boolean",
		},
		className: {
			description: "Additional CSS classes for styling",
		},
	},
	args: {
		currentVersionIndex: 1,
		isRestoring: false,
		handleVersionChange: fn(),
		onRestore: fn(),
	},
} satisfies Meta<typeof VersionFooter>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default version footer with restore capability.
 */
export const Default: Story = {
	args: {},
	decorators: [
		(Story) => (
			<div className="relative h-[400px] bg-background border border-border rounded-lg overflow-hidden">
				<div className="p-6">
					<h3 className="text-lg font-semibold mb-4">Document Content</h3>
					<p className="text-muted-foreground">
						You are viewing a previous version of this document. Use the footer controls to restore
						or navigate back to the latest version.
					</p>
				</div>
				<Story />
			</div>
		),
	],
};

/**
 * Version footer with restore in progress.
 */
export const Restoring: Story = {
	args: {
		isRestoring: true,
	},
	decorators: [
		(Story) => (
			<div className="relative h-[400px] bg-background border border-border rounded-lg overflow-hidden">
				<div className="p-6">
					<h3 className="text-lg font-semibold mb-4">Document Content</h3>
					<p className="text-muted-foreground">
						Restore operation is in progress. The restore button shows a loading state.
					</p>
				</div>
				<Story />
			</div>
		),
	],
};

/**
 * Interactive version footer with state management.
 */
export const Interactive: Story = {
	args: {},
	render: (args) => {
		const [isRestoring, setIsRestoring] = useState(false);
		const [currentIndex, setCurrentIndex] = useState(1);

		const handleVersionChange = (type: "next" | "prev" | "toggle" | "latest") => {
			console.log(`Version change: ${type}`);
			if (type === "latest") {
				setCurrentIndex(0);
			}
		};

		const handleRestore = () => {
			setIsRestoring(true);
			// Simulate async restore operation
			setTimeout(() => {
				setIsRestoring(false);
				console.log("Restore completed");
			}, 2000);
		};

		return (
			<div className="relative h-[400px] bg-background border border-border rounded-lg overflow-hidden">
				<div className="p-6">
					<h3 className="text-lg font-semibold mb-4">Interactive Document</h3>
					<p className="text-muted-foreground mb-4">
						Click "Restore this version" to see the loading state, or "Back to latest" to navigate.
					</p>
					<div className="text-sm text-muted-foreground">
						<div>Current Version Index: {currentIndex}</div>
						<div>Is Restoring: {isRestoring ? "Yes" : "No"}</div>
					</div>
				</div>
				<VersionFooter
					{...args}
					currentVersionIndex={currentIndex}
					isRestoring={isRestoring}
					handleVersionChange={handleVersionChange}
					onRestore={handleRestore}
				/>
			</div>
		);
	},
};

/**
 * Version footer with custom styling.
 */
export const CustomStyled: Story = {
	args: {
		className: "border-t-2 border-blue-500 bg-blue-50",
	},
	decorators: [
		(Story) => (
			<div className="relative h-[400px] bg-background border border-border rounded-lg overflow-hidden">
				<div className="p-6">
					<h3 className="text-lg font-semibold mb-4">Document Content</h3>
					<p className="text-muted-foreground">Version footer with custom blue-themed styling.</p>
				</div>
				<Story />
			</div>
		),
	],
};

/**
 * Version footer in mobile viewport simulation.
 */
export const Mobile: Story = {
	args: {},
	parameters: {
		viewport: {
			defaultViewport: "mobile1",
		},
	},
	decorators: [
		(Story) => (
			<div className="relative h-[500px] bg-background border border-border rounded-lg overflow-hidden">
				<div className="p-4">
					<h3 className="text-lg font-semibold mb-4">Mobile View</h3>
					<p className="text-sm text-muted-foreground">
						Version footer adapts to mobile layout with vertical button stacking.
					</p>
				</div>
				<Story />
			</div>
		),
	],
};

/**
 * Version footer showing different version indexes.
 */
export const DifferentVersions: Story = {
	args: {},
	render: () => {
		const versions = [0, 1, 2];

		return (
			<div className="space-y-6">
				{versions.map((versionIndex) => (
					<div
						key={versionIndex}
						className="relative h-[300px] bg-background border border-border rounded-lg overflow-hidden"
					>
						<div className="p-4">
							<h4 className="font-semibold mb-2">Version Index: {versionIndex}</h4>
							<p className="text-sm text-muted-foreground">
								{versionIndex === 0 && "Latest version (most recent)"}
								{versionIndex === 1 && "Previous version"}
								{versionIndex === 2 && "Oldest version"}
							</p>
						</div>
						<VersionFooter
							currentVersionIndex={versionIndex}
							isRestoring={false}
							handleVersionChange={fn()}
							onRestore={fn()}
						/>
					</div>
				))}
			</div>
		);
	},
	parameters: {
		layout: "padded",
	},
};
