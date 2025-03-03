import { Component, computed, input } from '@angular/core';
import { hlm } from '@spartan-ng/brain/core';
import { ClassValue } from 'clsx';

@Component({
  selector: 'app-table',
  template: `<table [class]="computedClass()">
    <ng-content />
  </table>`,
  host: {
    class: 'relative w-full overflow-auto'
  }
})
export class TableComponent {
  class = input<ClassValue>();
  computedClass = computed(() => hlm('w-full caption-bottom text-sm', this.class()));
}
