interface LegalPageProps {
	title: string;
	content: string;
}

export function LegalPage({ title, content }: LegalPageProps) {
	return (
		<div className="max-w-4xl mx-auto flex flex-col gap-4">
			<h1 className="text-3xl font-bold">{title}</h1>
			<div
				className="prose dark:prose-invert max-w-none"
				dangerouslySetInnerHTML={{ __html: content }}
			/>
		</div>
	);
}
