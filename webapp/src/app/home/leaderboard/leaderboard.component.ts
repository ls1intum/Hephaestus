import { Component, input } from '@angular/core';
import { NgIconComponent } from '@ng-icons/core';
import { octFileDiff, octCheck, octComment, octGitPullRequest, octChevronRight, octEye } from '@ng-icons/octicons';
import { LeaderboardEntry } from 'app/core/modules/openapi';
import { AvatarFallbackComponent } from 'app/ui/avatar/avatar-fallback.component';
import { AvatarImageComponent } from 'app/ui/avatar/avatar-image.component';
import { AvatarComponent } from 'app/ui/avatar/avatar.component';
import { TableBodyDirective } from 'app/ui/table/table-body.directive';
import { TableCaptionDirective } from 'app/ui/table/table-caption.directive';
import { TableCellDirective } from 'app/ui/table/table-cell.directive';
import { TableFooterDirective } from 'app/ui/table/table-footer.directive';
import { TableHeadDirective } from 'app/ui/table/table-head.directive';
import { TableHeaderDirective } from 'app/ui/table/table-header.directive';
import { TableRowDirective } from 'app/ui/table/table-row.directive';
import { TableComponent } from 'app/ui/table/table.component';

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [
    AvatarComponent,
    AvatarFallbackComponent,
    AvatarImageComponent,
    TableComponent,
    TableBodyDirective,
    TableCaptionDirective,
    TableCellDirective,
    TableFooterDirective,
    TableHeaderDirective,
    TableHeadDirective,
    TableRowDirective,
    NgIconComponent
  ],
  templateUrl: './leaderboard.component.html'
})
export class LeaderboardComponent {
  protected octFileDiff = octFileDiff;
  protected octCheck = octCheck;
  protected octComment = octComment;
  protected octGitPullRequest = octGitPullRequest;
  protected octChevronRight = octChevronRight;
  protected octEye = octEye;

  leaderboard = input<LeaderboardEntry[]>();
}
