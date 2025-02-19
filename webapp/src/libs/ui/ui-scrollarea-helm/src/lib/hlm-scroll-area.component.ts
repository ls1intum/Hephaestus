import { BooleanInput } from '@angular/cdk/coercion';
import {
	ChangeDetectionStrategy,
	Component,
	ViewEncapsulation,
	booleanAttribute,
	computed,
	input,
  viewChild,
} from '@angular/core';
import { hlm } from '@spartan-ng/ui-core';
import type { ClassValue } from 'clsx';
import { NgScrollbar, NgScrollbarModule } from 'ngx-scrollbar';

@Component({
    selector: 'hlm-scroll-area',
    imports: [NgScrollbarModule],
    template: `
		<ng-scrollbar
      #scrollbar
			[visibility]="visibility()"
			[autoHeightDisabled]="autoHeightDisabled()"
			[autoWidthDisabled]="autoWidthDisabled()"
			[track]="track()"
			[style]="{
				'--scrollbar-border-radius': '100px',
				'--scrollbar-padding': '1px',
				'--scrollbar-thumb-color': 'hsl(var(--border))',
				'--scrollbar-thumb-hover-color': 'hsl(var(--border))',
				'--scrollbar-size': '7px',
			}"
		>
			<ng-content />
		</ng-scrollbar>
	`,
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    host: {
        '[class]': '_computedClass()',
    }
})
export class HlmScrollAreaComponent {
  protected readonly _computedClass = computed(() => hlm('block', this.userClass()));

  public readonly scrollbar = viewChild.required(NgScrollbar);
	public readonly userClass = input<ClassValue>('', { alias: 'class' });
	public readonly track = input<'vertical' | 'horizontal' | 'all'>('all');
	public readonly autoHeightDisabled = input<boolean, BooleanInput>(true, { transform: booleanAttribute });
	public readonly autoWidthDisabled = input<boolean, BooleanInput>(true, { transform: booleanAttribute });
	public readonly visibility = input<'hover' | 'always' | 'native'>('native');
}
