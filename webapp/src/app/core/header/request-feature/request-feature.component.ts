import { booleanAttribute, Component, input } from '@angular/core';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmTooltipModule } from '@spartan-ng/ui-tooltip-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideSparkles } from '@ng-icons/lucide';
import { BrnTooltipContentDirective } from '@spartan-ng/brain/tooltip';

@Component({
  selector: 'app-request-feature',
  templateUrl: './request-feature.component.html',
  imports: [HlmButtonModule, BrnTooltipContentDirective, NgIconComponent, HlmTooltipModule],
  providers: [provideIcons({ lucideSparkles })]
})
export class RequestFeatureComponent {
  iconOnly = input(false, { transform: booleanAttribute });
}
