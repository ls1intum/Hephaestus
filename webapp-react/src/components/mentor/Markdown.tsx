import { cn } from "@/lib/utils";
import { omit } from "lodash";
import { useContext, useMemo } from "react";
import ReactMarkdown from "react-markdown";
import rehypeKatex from "rehype-katex";
import rehypeRaw from "rehype-raw";
import remarkDirective from "remark-directive";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import type { PluggableList } from "unified";
import { visit } from "unist-util-visit";

import { ChainlitContext, type IMessageElement } from "@chainlit/react-client";

import { AspectRatio } from "@/components/ui/aspect-ratio";
import { Card } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";

import BlinkingCursor from "./BlinkingCursor";
import CodeSnippet from "./CodeSnippet";
import { ElementRef } from "./Elements/ElementRef";
import {
	type AlertProps,
	MarkdownAlert,
	alertComponents,
	normalizeAlertType,
} from "./MarkdownAlert";

// Define types to replace 'any'
interface TreeNode {
	type: string;
	value?: string;
	children?: TreeNode[];
	data?: {
		hName?: string;
		hProperties?: Record<string, unknown>;
	};
}

interface Props {
	allowHtml?: boolean;
	latex?: boolean;
	refElements?: IMessageElement[];
	children: string;
	className?: string;
}

const cursorPlugin = () => {
	return (tree: TreeNode) => {
		visit(
			tree,
			"text",
			(node: TreeNode, index: number, parent: { children?: TreeNode[] }) => {
				const placeholderPattern = /\u200B/g;
				const matches = [...(node.value?.matchAll(placeholderPattern) || [])];

				if (matches.length > 0) {
					const newNodes: TreeNode[] = [];
					let lastIndex = 0;

					for (const match of matches) {
						const [fullMatch] = match;
						const startIndex = match.index !== undefined ? match.index : 0;
						const endIndex = startIndex + fullMatch.length;

						if (startIndex > lastIndex) {
							newNodes.push({
								type: "text",
								value: node.value?.slice(lastIndex, startIndex),
							});
						}

						newNodes.push({
							type: "blinkingCursor",
							data: {
								hName: "blinkingCursor",
								hProperties: { text: "Blinking Cursor" },
							},
						});

						lastIndex = endIndex;
					}

					if (lastIndex < (node.value?.length ?? 0)) {
						newNodes.push({
							type: "text",
							value: node.value?.slice(lastIndex),
						});
					}

					if (parent?.children && Array.isArray(parent.children)) {
						parent.children.splice(index, 1, ...newNodes);
					}
				}
			},
		);
	};
};

const Markdown = ({
	allowHtml,
	latex,
	refElements,
	className,
	children,
}: Props) => {
	const apiClient = useContext(ChainlitContext);

	const rehypePlugins = useMemo(() => {
		let rehypePlugins: PluggableList = [];
		if (allowHtml) {
			rehypePlugins = [
				rehypeRaw as unknown as PluggableList[number],
				...rehypePlugins,
			];
		}
		if (latex) {
			rehypePlugins = [
				rehypeKatex as unknown as PluggableList[number],
				...rehypePlugins,
			];
		}
		return rehypePlugins;
	}, [allowHtml, latex]);

	const remarkPlugins = useMemo(() => {
		let remarkPlugins: PluggableList = [
			cursorPlugin,
			remarkGfm as unknown as PluggableList[number],
			remarkDirective as unknown as PluggableList[number],
			MarkdownAlert,
		];

		if (latex) {
			remarkPlugins = [
				...remarkPlugins,
				remarkMath as unknown as PluggableList[number],
			];
		}
		return remarkPlugins;
	}, [latex]);

	return (
		<ReactMarkdown
			className={cn("prose lg:prose-xl", className)}
			remarkPlugins={remarkPlugins}
			rehypePlugins={rehypePlugins}
			components={{
				...alertComponents, // add alert components
				code(props) {
					return (
						<code
							{...omit(props, ["node"])}
							className="relative rounded bg-muted px-[0.3rem] py-[0.2rem] font-mono text-sm font-semibold"
						/>
					);
				},
				pre(props) {
					// CodeSnippet requires children to be passed explicitly
					return (
						<CodeSnippet {...omit(props, ["node"])}>
							{props.children}
						</CodeSnippet>
					);
				},
				a({ children, ...props }) {
					const name = children as string;
					const element = refElements?.find((e) => e.name === name);
					if (element) {
						return <ElementRef element={element} />;
					}
					return (
						<a
							{...props}
							className="text-primary hover:underline"
							target="_blank"
						>
							{children}
						</a>
					);
				},
				img(props) {
					return (
						<div className="sm:max-w-sm md:max-w-md">
							<AspectRatio
								ratio={16 / 9}
								className="bg-muted rounded-md overflow-hidden"
							>
								<img
									src={
										(props.src as string)?.startsWith("/public")
											? apiClient.buildEndpoint(props.src as string)
											: (props.src as string)
									}
									alt={props.alt as string}
									className="h-full w-full object-contain"
								/>
							</AspectRatio>
						</div>
					);
				},
				blockquote(props) {
					return (
						<blockquote
							{...omit(props, ["node"])}
							className="mt-6 border-l-2 pl-6 italic"
						/>
					);
				},
				em(props) {
					return <span {...omit(props, ["node"])} className="italic" />;
				},
				strong(props) {
					return <span {...omit(props, ["node"])} className="font-bold" />;
				},
				hr() {
					return <Separator />;
				},
				ul(props) {
					return (
						<ul
							{...omit(props, ["node"])}
							className="my-3 ml-3 list-disc pl-2 [&>li]:mt-1"
						/>
					);
				},
				ol(props) {
					return (
						<ol
							{...omit(props, ["node"])}
							className="my-3 ml-3 list-decimal pl-2 [&>li]:mt-1"
						/>
					);
				},
				h1(props) {
					return (
						<h1
							{...omit(props, ["node"])}
							className="scroll-m-20 text-4xl font-extrabold tracking-tight lg:text-5xl mt-8 first:mt-0"
						/>
					);
				},
				h2(props) {
					return (
						<h2
							{...omit(props, ["node"])}
							className="scroll-m-20 border-b pb-2 text-3xl font-semibold tracking-tight mt-8 first:mt-0"
						/>
					);
				},
				h3(props) {
					return (
						<h3
							{...omit(props, ["node"])}
							className="scroll-m-20 text-2xl font-semibold tracking-tight mt-6 first:mt-0"
						/>
					);
				},
				h4(props) {
					return (
						<h4
							{...omit(props, ["node"])}
							className="scroll-m-20 text-xl font-semibold tracking-tight mt-6 first:mt-0"
						/>
					);
				},
				p(props) {
					return (
						<div
							{...omit(props, ["node"])}
							className="leading-7 [&:not(:first-child)]:mt-4 whitespace-pre-wrap break-words"
							role="article"
						/>
					);
				},
				table({ children, ...props }) {
					return (
						<Card className="[&:not(:first-child)]:mt-2 [&:not(:last-child)]:mb-2">
							<Table {...omit(props, ["node"])}>{children}</Table>
						</Card>
					);
				},
				thead({ children, ...props }) {
					return (
						<TableHeader {...omit(props, ["node"])}>{children}</TableHeader>
					);
				},
				tr({ children, ...props }) {
					return <TableRow {...omit(props, ["node"])}>{children}</TableRow>;
				},
				th({ children, ...props }) {
					return <TableHead {...omit(props, ["node"])}>{children}</TableHead>;
				},
				td({ children, ...props }) {
					return <TableCell {...omit(props, ["node"])}>{children}</TableCell>;
				},
				tbody({ children, ...props }) {
					return <TableBody {...omit(props, ["node"])}>{children}</TableBody>;
				},
				// @ts-expect-error custom plugin
				blinkingCursor: () => <BlinkingCursor whitespace />,
				alert: ({
					type,
					children,
					...props
				}: AlertProps & { type?: string }) => {
					const alertType = normalizeAlertType(type || props.variant || "info");
					return alertComponents.Alert({ variant: alertType, children });
				},
			}}
		>
			{children}
		</ReactMarkdown>
	);
};

export { Markdown };
