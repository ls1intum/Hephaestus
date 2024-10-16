import { Component, computed, inject } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octClockFill } from '@ng-icons/octicons';
import { UserService } from 'app/core/modules/openapi';
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
import { combineLatest, lastValueFrom, map, timer } from 'rxjs';
import { CircleX, LucideAngularModule, Info } from 'lucide-angular';
import dayjs from 'dayjs';
import advancedFormat from 'dayjs/plugin/advancedFormat';

dayjs.extend(advancedFormat);

const repoImages: { [key: string]: string } = {
  Hephaestus: 'https://github.com/ls1intum/Hephaestus/raw/refs/heads/develop/docs/images/hammer.svg',
  Artemis: 'https://artemis.in.tum.de/public/images/logo.png',
  Athena: 'https://raw.githubusercontent.com/ls1intum/Athena/develop/playground/public/logo.png'
};

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
  protected Info = Info;
  // get user id from the url
  protected userLogin: string | null = null;

  displayFirstContribution = computed(() => {
    if (this.query.data()) {
      return dayjs(this.query.data()?.firstContribution).format('Do [of] MMMM YYYY');
    }
    return '';
  });

  constructor(private route: ActivatedRoute) {
    this.userLogin = this.route.snapshot.paramMap.get('id');
  }

  getRepositoryImage = (name: string) => repoImages[name.split('/')[1]] || 'https://avatars.githubusercontent.com/u/11064260?v=4';

  query = injectQuery(() => ({
    queryKey: ['user', { id: this.userLogin }],
    queryFn: async () => lastValueFrom(combineLatest([this.userService.getUserProfile(this.userLogin ?? 'testuser'), timer(400)]).pipe(map(([user]) => user)))
  }));
}
