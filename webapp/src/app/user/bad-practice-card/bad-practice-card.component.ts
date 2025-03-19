import { Component, input } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { octCheck, octX } from '@ng-icons/octicons';
import { PullRequestBadPractice } from '@app/core/modules/openapi';

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

  // Mapping states to emojis and Tailwind styles
  stateConfig = {
    GOOD_PRACTICE: { emoji: '🚀', text: 'Good Practice', bg: 'bg-green-100 text-green-800' },
    FIXED: { emoji: '✅', text: 'Fixed', bg: 'bg-blue-100 text-blue-800' },
    CRITICAL_ISSUE: { emoji: '🔥', text: 'Critical Issue', bg: 'bg-red-100 text-red-800' },
    NORMAL_ISSUE: { emoji: '⚠️', text: 'Normal Issue', bg: 'bg-yellow-100 text-yellow-800' },
    MINOR_ISSUE: { emoji: '🟡', text: 'Minor Issue', bg: 'bg-gray-100 text-gray-800' },
    WONT_FIX: { emoji: '🚫', text: "Won't Fix", bg: 'bg-gray-300 text-gray-900' }
  };

  getEmoji(): string {
    return this.state() ? this.stateConfig[this.state()].emoji : '❓';
  }
}
