import { Component, input } from '@angular/core';
import { PullRequest } from '@app/core/modules/openapi';

@Component({
  selector: 'app-pull-request-widget',
  templateUrl: './pull-request-widget.component.html',
  standalone: true
})
export class PullRequestWidgetComponent {
  pullRequest = input.required<PullRequest>();
}
