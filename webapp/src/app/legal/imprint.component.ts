import { Component } from '@angular/core';
import { environment } from 'environments/environment';

@Component({
  selector: 'app-imprint',
  template: `
    <div class="container prose dark:prose-invert">
      <h1>Imprint</h1>
      <div [innerHTML]="imprintHtml"></div>
    </div>
  `
})
export class ImprintComponent {
  imprintHtml = environment.legal.imprintHtml;
}
