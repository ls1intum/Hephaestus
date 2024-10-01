import { Component, input, output } from '@angular/core';

export type SelectOption = {
  id: number;
  value: string;
  label: string;
};

@Component({
  selector: 'app-select',
  standalone: true,
  templateUrl: './select.component.html'
})
export class SelectComponent {
  options = input.required<SelectOption[]>();
  defaultOption = input<string>();
  selectChange = output<string>();
  name = input<string>();

  onSelectChange(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    this.selectChange.emit(value);
  }
}
