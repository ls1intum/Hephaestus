import { setProjectAnnotations } from '@storybook/react-vite';
import * as projectAnnotations from './preview';

// This is an important step to apply the right configuration when testing your stories.
// More info at: https://storybook.js.org/docs/api/portable-stories/portable-stories-vitest#setprojectannotations
setProjectAnnotations([projectAnnotations]);

// jsdom has no ResizeObserver; cmdk's <Command.List> observes its own size to size the popup.
// A no-op stub is enough for interaction tests — no assertions depend on the measurements.
if (typeof globalThis.ResizeObserver === 'undefined') {
	globalThis.ResizeObserver = class ResizeObserver {
		observe() {}
		unobserve() {}
		disconnect() {}
	};
}

// jsdom has no scrollIntoView either; cmdk calls it to keep the highlighted option in view.
if (typeof Element.prototype.scrollIntoView !== 'function') {
	Element.prototype.scrollIntoView = () => {};
}
