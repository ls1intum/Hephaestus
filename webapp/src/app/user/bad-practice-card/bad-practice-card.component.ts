import { Component, input } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { octCheck, octX } from '@ng-icons/octicons';
import { PullRequestBadPractice } from '@app/core/modules/openapi';
import { stateConfig } from '@app/utils';

@Component({
  selector: 'app-bad-practice-card',
  imports: [HlmCardModule],
  templateUrl: './bad-practice-card.component.html',
  styles: ``
})
export class BadPracticeCardComponent {
  protected readonly octCheck = octCheck;
  protected readonly octX = octX;

  title = input.required<string>();
  description = input.required<string>();
  state = input.required<PullRequestBadPractice.StateEnum>();

  getEmoji(): string {
    return this.state() ? stateConfig[this.state()].emoji : '❓';
  }
}
