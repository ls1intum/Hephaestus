import { ChangeDetectionStrategy, Component, ViewEncapsulation } from '@angular/core';
import { BrnTooltipDirective } from '@spartan-ng/ui-tooltip-brain';

@Component({
    selector: 'hlm-tooltip',
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [],
    providers: [],
    host: {
        '[style]': '{display: "contents"}',
    },
    hostDirectives: [BrnTooltipDirective],
    template: `
		<ng-content />
	`
})
export class HlmTooltipComponent {}
