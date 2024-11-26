import { booleanAttribute, Component, input } from '@angular/core';
import { RouterModule } from '@angular/router';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { BrnTooltipContentDirective } from '@spartan-ng/ui-tooltip-brain';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { LucideAngularModule, BotMessageSquare } from 'lucide-angular';

@Component({
  selector: 'app-ai-mentor',
  standalone: true,
  imports: [LucideAngularModule, HlmButtonModule, HlmTooltipComponent, HlmTooltipTriggerDirective, BrnTooltipContentDirective, RouterModule],
  templateUrl: './ai-mentor.component.html'
})
export class AiMentorComponent {
  protected BotMessageSquare = BotMessageSquare;

  iconOnly = input(false);
}
