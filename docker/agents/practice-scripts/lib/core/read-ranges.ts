/** Continuous line coverage, shared by governed evidence reads and native review tools. */
export class ReadRanges {
	readonly #files = new Map<string, { last?: number; extents: { start: number; end: number }[] }>();

	expect(key: string): void {
		if (!this.#files.has(key)) {
			this.#files.set(key, { extents: [] });
		}
	}

	add(key: string, start: number, end: number, last?: number): void {
		this.expect(key);
		const file = this.#files.get(key);
		if (file === undefined) {
			return;
		}
		if (last !== undefined) {
			file.last = last;
		}
		if (start >= 1 && end >= start) {
			file.extents.push({ start, end });
		}
		file.extents.sort((a, b) => a.start - b.start);
		const merged: { start: number; end: number }[] = [];
		for (const range of file.extents) {
			const previous = merged.at(-1);
			if (previous !== undefined && range.start <= previous.end + 1) {
				previous.end = Math.max(previous.end, range.end);
			} else {
				merged.push({ ...range });
			}
		}
		file.extents = merged;
	}

	complete(key: string): boolean {
		const file = this.#files.get(key);
		const first = file?.extents[0];
		return file?.last !== undefined && first?.start === 1 && first.end >= file.last;
	}

	/** Unread lines in `1..last`, or undefined while the file length is unknown. */
	gaps(key: string): { start: number; end: number }[] | undefined {
		const file = this.#files.get(key);
		if (file?.last === undefined) {
			return undefined;
		}
		const gaps: { start: number; end: number }[] = [];
		let next = 1;
		for (const range of file.extents) {
			if (next > file.last) {
				break;
			}
			if (range.start > next) {
				gaps.push({ start: next, end: Math.min(range.start - 1, file.last) });
			}
			next = Math.max(next, range.end + 1);
		}
		if (next <= file.last) {
			gaps.push({ start: next, end: file.last });
		}
		return gaps;
	}

	get incomplete(): string[] {
		return [...this.#files.keys()].filter((key) => !this.complete(key));
	}
}
