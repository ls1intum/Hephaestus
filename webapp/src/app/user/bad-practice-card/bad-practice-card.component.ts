import { Component, input } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { NgIcon } from '@ng-icons/core';
import { octCheck, octX } from '@ng-icons/octicons';

@Component({
  selector: 'app-bad-practice-card',
  standalone: true,
  imports: [HlmCardModule, NgIcon],
  templateUrl: './bad-practice-card.component.html',
  styles: ``
})
export class BadPracticeCardComponent {
  protected readonly octCheck = octCheck;
  protected readonly octX = octX;

  title = input<string>();
  description = input<string>();
  resolved = input<boolean>();
}
