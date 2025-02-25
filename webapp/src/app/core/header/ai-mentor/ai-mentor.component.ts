import { booleanAttribute, Component, input } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { BrnTooltipContentDirective } from '@spartan-ng/brain/tooltip';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { lucideBotMessageSquare } from '@ng-icons/lucide';

@Component({
  selector: 'app-ai-mentor',
  imports: [HlmButtonModule, HlmTooltipComponent, HlmTooltipTriggerDirective, BrnTooltipContentDirective, RouterModule, NgIconComponent],
  providers: [provideIcons({ lucideBotMessageSquare })],
  templateUrl: './ai-mentor.component.html'
})
export class AiMentorComponent {
  iconOnly = input(false, { transform: booleanAttribute });
}
