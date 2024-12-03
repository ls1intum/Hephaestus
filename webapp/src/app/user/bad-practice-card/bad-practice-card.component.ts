import { Component, input } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';

@Component({
  selector: 'app-bad-practice-card',
  standalone: true,
  imports: [HlmCardModule],
  templateUrl: './bad-practice-card.component.html',
  styles: ``
})
export class BadPracticeCardComponent {
  title = input<string>();
  description = input<string>();
  repositoryName = input<string>();
  number = input<number>();
}
