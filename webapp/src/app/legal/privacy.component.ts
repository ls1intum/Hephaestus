import { Component } from '@angular/core';
import { environment } from 'environments/environment';

@Component({
  selector: 'app-privacy',
  standalone: true,
  template: `
    <div class="container prose dark:prose-invert">
      <h1>Privacy</h1>
      <div [innerHTML]="imprintHtml"></div>
    </div>
  `
})
export class PrivacyComponent {
  imprintHtml = environment.legal.privacyHtml;
}
