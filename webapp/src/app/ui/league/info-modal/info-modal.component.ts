import { Component } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { BrnDialogContentDirective, BrnDialogTriggerDirective } from '@spartan-ng/brain/dialog';
import { HlmDialogComponent, HlmDialogContentComponent, HlmDialogHeaderComponent } from '@spartan-ng/ui-dialog-helm';
import { LucideAngularModule, Info, Star } from 'lucide-angular';
import { Leagues } from '@app/utils';
import { LeagueIconComponent } from '@app/ui/league/icon/league-icon.component';

@Component({
  selector: 'app-league-info-modal',
  imports: [
    HlmCardModule,
    HlmButtonModule,
    HlmDialogComponent,
    HlmDialogContentComponent,
    HlmDialogHeaderComponent,
    BrnDialogContentDirective,
    BrnDialogTriggerDirective,
    LucideAngularModule,
    LeagueIconComponent
  ],
  templateUrl: './info-modal.component.html'
})
export class LeagueInfoModalComponent {
  protected Info = Info;
  protected Star = Star;

  protected Infinity = Infinity;
  protected Leagues = Leagues;
}
