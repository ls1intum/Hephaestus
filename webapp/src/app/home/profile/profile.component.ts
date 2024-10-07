import { Component, input, signal } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octClockFill } from '@ng-icons/octicons';
import { PullRequestDTO, PullRequestReview } from 'app/core/modules/openapi';
import { TableBodyDirective } from 'app/ui/table/table-body.directive';
import { TableCaptionDirective } from 'app/ui/table/table-caption.directive';
import { TableCellDirective } from 'app/ui/table/table-cell.directive';
import { TableFooterDirective } from 'app/ui/table/table-footer.directive';
import { TableHeadDirective } from 'app/ui/table/table-head.directive';
import { TableHeaderDirective } from 'app/ui/table/table-header.directive';
import { TableRowDirective } from 'app/ui/table/table-row.directive';
import { TableComponent } from 'app/ui/table/table.component';
import { HlmAvatarModule } from '@spartan-ng/ui-avatar-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { HlmIconModule } from '../../../libs/ui/ui-icon-helm/src/index';
import { BrnTooltipContentDirective } from '@spartan-ng/ui-tooltip-brain';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { HlmScrollAreaComponent } from '@spartan-ng/ui-scrollarea-helm';
import { ProfileActivityCardComponent } from '../../core/profile-activity-card/profile-activity-card.component';
import { IssueCardComponent } from '../../core/issue-card/issue-card.component';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    HlmAvatarModule,
    HlmSkeletonModule,
    TableComponent,
    TableBodyDirective,
    TableCaptionDirective,
    TableCellDirective,
    TableFooterDirective,
    TableHeaderDirective,
    TableHeadDirective,
    TableRowDirective,
    NgIconComponent,
    HlmIconModule,
    HlmTooltipComponent,
    HlmTooltipTriggerDirective,
    BrnTooltipContentDirective,
    HlmButtonModule,
    HlmScrollAreaComponent,
    ProfileActivityCardComponent,
    IssueCardComponent
  ],
  templateUrl: './profile.component.html'
})
export class ProfileComponent {
  protected octClockFill = octClockFill;
  // get user id from the url
  protected userId: string | null = null;

  userData = signal({
    name: 'GODrums',
    avatarUrl: 'https://github.com/godrums.png',
    first_contribution: '2024-08-01',
    repositories: ['ls1intum/Hephaestus', 'ls1intum/ls1intum/Artemis'],
    activity: [
      {
        id: 1,
        createdAt: '2024-10-06',
        updatedAt: '2024-10-06',
        state: 'CHANGES_REQUESTED',
        submittedAt: '2024-10-06',
        pullRequest: {
          title: 'Add feature Y',
          state: 'OPEN',
          number: 100,
          repository: {
            name: 'Hephaestus',
            nameWithOwner: 'ls1intum/Hephaestus',
            defaultBranch: 'develop',
            visibility: 'PUBLIC',
            url: 'https://github.com/ls1intum/Hephaestus'
          }
        }
      },
      {
        id: 2,
        createdAt: '2024-10-07',
        updatedAt: '2024-10-07',
        state: 'APPROVED',
        submittedAt: '2024-10-07',
        pullRequest: {
          title: 'Add feature X',
          state: 'CLOSED',
          number: 99,
          repository: {
            name: 'Hephaestus',
            nameWithOwner: 'ls1intum/Hephaestus',
            defaultBranch: 'develop',
            visibility: 'PUBLIC',
            url: 'https://github.com/ls1intum/Hephaestus'
          }
        }
      }
    ] as PullRequestReview[],
    pullRequests: [
      {
        title: 'Add feature X',
        state: 'OPEN',
        createdAt: '2024-10-04',
        repository: {
          name: 'Hephaestus',
          nameWithOwner: 'ls1intum/Hephaestus',
          url: 'https://github.com/ls1intum/Hephaestus'
        }
      }
    ] as PullRequestDTO[]
  });

  constructor(private route: ActivatedRoute) {
    this.userId = this.route.snapshot.paramMap.get('id');
    console.log(this.userId);
  }

  // query = injectQuery(() => ({
  //   queryKey: ['user', { id: this.userId }],
  //   queryFn: async () => lastValueFrom()
  // }));
}
