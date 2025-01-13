import { Component, inject } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { WorkspaceService } from '@app/core/modules/openapi';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { WorkspaceRulesTableComponent } from '@app/workspace/badpractices/table/rules-table/rules-table.component';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { FormsModule } from '@angular/forms';
import { HlmTdComponent, HlmThComponent } from '@spartan-ng/ui-table-helm';

@Component({
  selector: 'app-workspace-rules',
  standalone: true,
  imports: [BrnSelectModule, HlmSelectModule, FormsModule, HlmThComponent, HlmTdComponent, HlmThComponent, WorkspaceRulesTableComponent],
  template: `
    <h1 class="text-3xl font-bold mb-4">Bad practice rules</h1>
    <app-workspace-rules-table [availableRepos]="allReposQuery.data()" />
  `
})
export class WorkspaceRulesComponent {
  protected workspaceService = inject(WorkspaceService);

  allReposQuery = injectQuery(() => ({
    queryKey: ['workspace', 'repositoriesToMonitor'],
    queryFn: async () => lastValueFrom(this.workspaceService.getRepositoriesToMonitor())
  }));
}
