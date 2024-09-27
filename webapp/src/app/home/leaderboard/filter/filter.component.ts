import { Component, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { SelectComponent, SelectOption } from 'app/ui/select/select.component';
import dayjs from 'dayjs';
import { LabelComponent } from '../../../ui/label/label.component';

@Component({
  selector: 'app-leaderboard-filter',
  standalone: true,
  imports: [SelectComponent, RouterLink, LabelComponent],
  templateUrl: './filter.component.html'
})
export class LeaderboardFilterComponent {
  after = input<string>();
  before = input<string>();

  options = signal<SelectOption[]>([]);

  constructor(private router: Router) {
    // get monday - sunday of last 4 weeks
    const options = new Array<SelectOption>();
    let currentDate = dayjs().day(1);
    for (let i = 0; i < 4; i++) {
      const newDate = currentDate.subtract(7, 'day');
      options.push({
        id: newDate.unix(),
        value: `${newDate.format('YYYY-MM-DD')}.${currentDate.subtract(1, 'day').format('YYYY-MM-DD')}`,
        label: `${newDate.format('MMM D')} - ${currentDate.subtract(1, 'day').format('MMM D')}`
      });
      currentDate = newDate;
    }
    this.options.set(options);
  }

  selectFn(value: string) {
    const dates = value.split('.');
    // change query params
    this.router.navigate([], {
      queryParams: {
        after: dates[0],
        before: dates[1]
      }
    });
  }
}
