import { Component, input } from '@angular/core';

@Component({
  selector: 'app-bad-practice-summary',
  imports: [],
  templateUrl: './bad-practice-summary.component.html',
  styles: ``
})
export class BadPracticeSummaryComponent {

  summary = input<string>();
}
