import { booleanAttribute, Component, input } from '@angular/core';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { BrnTooltipContentDirective } from '@spartan-ng/brain/tooltip';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideSparkles } from '@ng-icons/lucide';

@Component({
  selector: 'app-request-feature',
  templateUrl: './request-feature.component.html',
  imports: [HlmButtonModule, HlmTooltipComponent, HlmTooltipTriggerDirective, BrnTooltipContentDirective, NgIconComponent],
  providers: [provideIcons({ lucideSparkles })]
})
export class RequestFeatureComponent {
  iconOnly = input(false, { transform: booleanAttribute });
}
