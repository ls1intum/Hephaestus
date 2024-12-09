import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import dayjs from 'dayjs/esm';
import isoWeek from 'dayjs/esm/plugin/isoWeek';
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
import { toSignal } from '@angular/core/rxjs-interop';

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
  private readonly route = inject(ActivatedRoute);
  private router = inject(Router);
  private queryParams = toSignal(this.route.queryParamMap, { requireSync: true });

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
    const formatted = dayjs().isoWeekday(day).startOf('hour').hour(hour).minute(minute).format('dddd, h:mm A');
    return {
      day,
      hour,
      minute,
      formatted
    };
  });

  getDefaultDate() {
    let defaultDate = dayjs().isoWeekday(this.leaderboardSchedule().day).startOf('hour').hour(this.leaderboardSchedule().hour).minute(this.leaderboardSchedule().minute);
    if (defaultDate.isAfter(dayjs())) {
      defaultDate = defaultDate.subtract(1, 'week');
    }
    return defaultDate;
  }

  after = computed(() =>
    this.queryParams().get('after') && this.queryParams().get('after')!.length > 0
      ? this.queryParams().get('after')
      : this.leaderboardSchedule()
        ? this.getDefaultDate().format()
        : ''
  );
  before = computed(() => this.queryParams().get('before') ?? (this.leaderboardSchedule() ? this.getDefaultDate().add(1, 'week').format() : dayjs().format()));
  selectValue = signal<string>(`${this.after()}.${this.before()}`);

  formattedDates = computed(() => {
    const [startDate, endDate] = [dayjs(this.after()), dayjs(this.before())];
    const sameMonth = startDate.month() === endDate.month();
    const endDateFormatted = endDate.isAfter(dayjs()) ? 'Now' : sameMonth ? endDate.format('D, h:mm A') : endDate.format('MMM D, h:mm A');
    if (sameMonth) {
      return `${startDate.format('MMM D, h:mm A')} - ${endDateFormatted}`;
    } else {
      return `${startDate.format('MMM D, h:mm A')} - ${endDateFormatted}`;
    }
  });

  placeholder = computed(() => {
    if (dayjs(this.after()).diff(this.before(), 'day') !== 7) {
      return 'Custom range';
    }
    return formatLabel(dayjs(dayjs()).diff(this.after(), 'week'));
  });

  options = computed(() => {
    let currentDate = this.getDefaultDate();
    let endDate = currentDate.add(1, 'week');
    const options: SelectOption[] = [
      {
        id: endDate.unix(),
        value: `${currentDate.format()}.${endDate.format()}`,
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

  constructor() {
    // persist date changes in url
    effect(() => {
      this.persistDateChange();
    });

    // make sure navigation to the page sets the default date
    this.route.queryParams.subscribe((params) => {
      if (!params['after'] && !params['before']) {
        this.selectValue.set(this.options()[0].value);
        this.persistDateChange();
      }
    });
  }

  protected persistDateChange() {
    if (this.selectValue().length === 1) return;

    const queryParams = this.router.parseUrl(this.router.url).queryParams;
    const dates = this.selectValue().split('.');
    if (dates.length !== 2) return;

    queryParams['after'] = dates[0];
    queryParams['before'] = dates[1];

    this.router.navigate([], {
      queryParams
    });
  }
}
