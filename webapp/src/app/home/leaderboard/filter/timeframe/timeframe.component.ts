import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import dayjs from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { HlmLabelModule } from '@spartan-ng/ui-label-helm';
import { BrnTooltipContentDirective } from '@spartan-ng/ui-tooltip-brain';
import { HlmTooltipComponent, HlmTooltipTriggerDirective } from '@spartan-ng/ui-tooltip-helm';
import { HlmIconComponent } from '@spartan-ng/ui-icon-helm';
import { provideIcons } from '@spartan-ng/ui-icon-helm';
import { lucideHelpCircle } from '@ng-icons/lucide';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { MetaService } from '@app/core/modules/openapi';

interface SelectOption {
  id: number;
  value: string;
  label: string;
}

dayjs.extend(isoWeek);

function formatLabel(weekIndex: number) {
  if (weekIndex === 0) {
    return 'Current week';
  }
  if (weekIndex === 1) {
    return 'Last week';
  }
  return `${weekIndex} weeks ago`;
}

@Component({
  selector: 'app-leaderboard-filter-timeframe',
  standalone: true,
  imports: [BrnSelectModule, HlmSelectModule, HlmLabelModule, FormsModule, HlmTooltipComponent, HlmTooltipTriggerDirective, BrnTooltipContentDirective, HlmIconComponent],
  providers: [provideIcons({ lucideHelpCircle })],
  templateUrl: './timeframe.component.html'
})
export class LeaderboardFilterTimeframeComponent {
  after = signal<string>('');
  before = signal<string>('');
  value = signal<string>(`${this.after()}.${this.before()}`);

  metaService = inject(MetaService);
  dateQuery = injectQuery(() => ({
    queryKey: ['meta'],
    queryFn: async () => lastValueFrom(this.metaService.getMetaData())
  }));

  leaderboardSchedule = computed(() => {
    const day = Number.parseInt(this.dateQuery.data()?.scheduledDay ?? '2');
    const timeParts = this.dateQuery.data()?.scheduledTime.split(':') ?? ['09', '00'];
    const hour = Number.parseInt(timeParts[0]);
    const minute = Number.parseInt(timeParts[1] ?? '0');
    return {
      day,
      hour,
      minute,
      formatted: dayjs().isoWeekday(day).startOf('hour').hour(hour).minute(minute).format('dddd, h:mm A')
    };
  });

  formattedDates = computed(() => {
    const currentOption = this.value() !== '.' ? this.options().find(option => option.value === this.value()) : this.options()[0];
    const [startDate, endDate] = currentOption!.value.split('.').map(date => dayjs(date));
    const sameMonth = startDate.month() === endDate.month();
    const endDateFormatted = endDate.isSame(dayjs(), 'minutes') ? 'Now' : (sameMonth ? endDate.format('D, h:mm A') : endDate.format('MMM D, h:mm A'));
    if (sameMonth) {
      return `${startDate.format('MMM D, h:mm A')} - ${endDateFormatted}`;
    } else {
      return `${startDate.format('MMM D, h:mm A')} - ${endDateFormatted}`;
    }
  });

  placeholder = computed(() => {
    return formatLabel(dayjs(dayjs()).diff(this.after(), 'week'));
  });

  options = computed(() => {
    const now = dayjs();
    let currentDate = dayjs().isoWeekday(this.leaderboardSchedule().day).startOf('hour').hour(this.leaderboardSchedule().hour).minute(this.leaderboardSchedule().minute);
    if (currentDate.isAfter(now)) {
      currentDate = currentDate.subtract(1, 'week');
    }
    const options: SelectOption[] = [
      {
        id: now.unix(),
        value: `${currentDate.format()}.${now.format()}`,
        label: formatLabel(0)
      }
    ];

    for (let i = 0; i < 4; i++) {
      const startDate = currentDate.subtract(1, 'week');
      options.push({
        id: startDate.unix(),
        value: `${startDate.format()}.${currentDate.format()}`,
        label: formatLabel(i + 1)
      });
      currentDate = startDate;
    }

    return options;
  });

  constructor(private router: Router) {
    // init params
    const queryParams = this.router.parseUrl(this.router.url).queryParams;
    this.after.set(
      queryParams['after'] ?? dayjs().isoWeekday(this.leaderboardSchedule().day).startOf('hour').hour(this.leaderboardSchedule().hour).minute(this.leaderboardSchedule().minute)
    );
    this.before.set(queryParams['before'] ?? dayjs().format());

    // persist changes in url
    effect(() => {
      if (this.value().length === 1) return;

      const queryParams = this.router.parseUrl(this.router.url).queryParams;
      const dates = this.value().split('.');
      queryParams['after'] = dates[0];
      queryParams['before'] = dates[1];

      this.router.navigate([], {
        queryParams
      });
    });
  }
}
