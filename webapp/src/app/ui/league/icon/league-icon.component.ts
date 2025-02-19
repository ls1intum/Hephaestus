import { Component, computed, input } from '@angular/core';
import { cn, getLeagueFromPoints } from '@app/utils';
import { LucideAngularModule, Medal } from 'lucide-angular';
import { type VariantProps, cva } from 'class-variance-authority';
import { LeagueBronzeIconComponent } from './league-bronze-icon.component';
import { LeagueNoneIconComponent } from './league-none-icon.component';
import { LeagueSilverIconComponent } from './league-silver-icon.component';
import { LeagueGoldIconComponent } from './league-gold-icon.component';
import { LeagueDiamondIconComponent } from './league-diamond-icon.component';
import { LeagueMasterIconComponent } from './league-master-icon.component';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { BrnTooltipContentDirective } from '@spartan-ng/ui-tooltip-brain';

export const leagueVariants = cva('size-8', {
  variants: {
    size: {
      default: '',
      sm: 'size-6',
      lg: 'size-12',
      max: 'size-28',
      full: 'size-full'
    },
    league: {
      none: 'text-gray-400',
      bronze: 'text-league-bronze',
      silver: 'text-league-silver',
      gold: 'text-league-gold',
      emerald: 'text-league-emerald',
      diamond: 'text-league-diamond',
      master: 'text-league-master'
    }
  },
  defaultVariants: {
    size: 'default',
    league: 'none'
  }
});
type LeagueVariants = VariantProps<typeof leagueVariants>;

@Component({
  selector: 'app-icon-league',
  standalone: true,
  imports: [
    LucideAngularModule,
    LeagueBronzeIconComponent,
    LeagueNoneIconComponent,
    LeagueSilverIconComponent,
    LeagueGoldIconComponent,
    LeagueDiamondIconComponent,
    LeagueMasterIconComponent,
    HlmTooltipComponent,
    HlmTooltipTriggerDirective,
    BrnTooltipContentDirective
  ],
  template: `
    <hlm-tooltip>
      @switch (league()) {
        @case ('none') {
          <app-league-none-icon [class]="computedClass()" hlmTooltipTrigger />
        }
        @case ('bronze') {
          <app-league-bronze-icon [class]="computedClass()" hlmTooltipTrigger />
        }
        @case ('silver') {
          <app-league-silver-icon [class]="computedClass()" hlmTooltipTrigger />
        }
        @case ('gold') {
          <app-league-gold-icon [class]="computedClass()" hlmTooltipTrigger />
        }
        @case ('diamond') {
          <app-league-diamond-icon [class]="computedClass()" hlmTooltipTrigger />
        }
        @case ('master') {
          <app-league-master-icon [class]="computedClass()" hlmTooltipTrigger />
        }
      }
      <span *brnTooltipContent class="flex items-center capitalize">
        {{ this.leagueTooltip() }}
      </span>
    </hlm-tooltip>
  `
})
export class LeagueIconComponent {
  protected Medal = Medal;

  size = input<LeagueVariants['size']>('default');
  leaguePoints = input<number>();
  class = input<string>('');

  league = computed(() => (this.leaguePoints() ? getLeagueFromPoints(this.leaguePoints()!)?.name.toLowerCase() : 'none'));
  computedClass = computed(() => cn(leagueVariants({ size: this.size(), league: this.league() as LeagueVariants['league'] }), this.class()));

  leagueTooltip = computed(() => {
    switch (this.league()) {
      case 'bronze':
        return 'Bronze League';
      case 'silver':
        return 'Silver League';
      case 'gold':
        return 'Gold League';
      case 'diamond':
        return 'Diamond League';
      case 'master':
        return 'Master League';
      default:
        return 'No League';
    }
  });
}
