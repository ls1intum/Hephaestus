import { Directive, computed, input } from '@angular/core';
import { hlm } from '@spartan-ng/brain/core';
import type { ClassValue } from 'clsx';

@Directive({
	selector: '[hlmSliderTickMarks]',
	host: {
		'[class]': '_computedClass()',
	},
})
export class HlmSliderTickMarksDirective {
	public readonly userClass = input<ClassValue>('', { alias: 'class' });
	protected _computedClass = computed(() =>
		hlm('h-full w-full relative -top-4 cursor-pointer pointer-events-none transition-all', this.userClass()),
	);
}
