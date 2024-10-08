import { Component, computed, inject, signal } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octClockFill } from '@ng-icons/octicons';
import { PullRequestDTO, PullRequestReview, UserService } from 'app/core/modules/openapi';
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
import { lastValueFrom } from 'rxjs';
import { CircleX, LucideAngularModule } from 'lucide-angular';
import dayjs from 'dayjs';
import advancedFormat from 'dayjs/plugin/advancedFormat';

dayjs.extend(advancedFormat);

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    LucideAngularModule,
    NgIconComponent,
    ProfileActivityCardComponent,
    IssueCardComponent,
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
    HlmIconModule,
    HlmTooltipComponent,
    HlmTooltipTriggerDirective,
    BrnTooltipContentDirective,
    HlmButtonModule,
    HlmScrollAreaComponent
  ],
  templateUrl: './profile.component.html'
})
export class ProfileComponent {
  userService = inject(UserService);

  protected octClockFill = octClockFill;
  protected CircleX = CircleX;
  // get user id from the url
  protected userLogin: string | null = null;

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

  displayFirstContribution = computed(() => {
    if (this.query.data()) {
      return dayjs(this.query.data()?.firstContribution).format('Do [of] MMMM YYYY');
    }
    return '';
  });

  constructor(private route: ActivatedRoute) {
    this.userLogin = this.route.snapshot.paramMap.get('id');
    console.log(this.userLogin);
  }

  getRepositoryImage = (name: string) => {
    const shortName = name.split('/')[1];
    switch (shortName) {
      case 'Hephaestus':
        return 'https://github.com/ls1intum/Hephaestus/raw/refs/heads/develop/docs/images/hammer.svg';
      case 'Artemis':
        return 'https://artemis.in.tum.de/public/images/logo.png';
      default:
        return '';
    }
  };

  query = injectQuery(() => ({
    queryKey: ['user', { id: this.userLogin }],
    queryFn: async () => lastValueFrom(this.userService.getUserProfile(this.userLogin ?? 'testuser'))
  }));
}
