import type { CellContext, ColumnDef, HeaderContext } from '@tanstack/angular-table';
import { FlexRenderDirective } from '@tanstack/angular-table';
import { Component, TemplateRef, viewChild } from '@angular/core';

@Component({
  template: './table.component.html',
  standalone: true,
  imports: [FlexRenderDirective]
})
export class TableComponent {
  customHeader = viewChild.required<TemplateRef<{ $implicit: HeaderContext<any, any> }>>('customHeader');
  customCell = viewChild.required<TemplateRef<{ $implicit: CellContext<any, any> }>>('customCell');

  columns: ColumnDef<unknown>[] = [
    {
      id: 'customCell',
      header: () => this.customHeader(),
      cell: () => this.customCell()
    }
  ];
}
