import { booleanAttribute, Component, input } from '@angular/core';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { BrnTooltipContentDirective } from '@spartan-ng/ui-tooltip-brain';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { LucideAngularModule, Sparkles } from 'lucide-angular';

@Component({
  selector: 'app-request-feature',
  imports: [LucideAngularModule, HlmButtonModule, HlmTooltipComponent, HlmTooltipTriggerDirective, BrnTooltipContentDirective],
  templateUrl: './request-feature.component.html'
})
export class RequestFeatureComponent {
  protected Sparkles = Sparkles;

  iconOnly = input(false, { transform: booleanAttribute });
}
