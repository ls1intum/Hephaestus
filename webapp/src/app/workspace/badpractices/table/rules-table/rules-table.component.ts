import { Component, computed, inject, input, signal, TrackByFunction } from '@angular/core';
import { BrnSelectModule } from '@spartan-ng/ui-select-brain';
import { HlmSelectModule } from '@spartan-ng/ui-select-helm';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrnTableModule } from '@spartan-ng/ui-table-brain';
import { HlmTableModule } from '@spartan-ng/ui-table-helm';
import { BadPracticeRuleService, PullRequestBadPracticeRule } from '@app/core/modules/openapi';
import { BrnPopoverComponent, BrnPopoverImports, BrnPopoverTriggerDirective } from '@spartan-ng/ui-popover-brain';
import { HlmPopoverModule } from '@spartan-ng/ui-popover-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { HlmMenuModule } from '@spartan-ng/ui-menu-helm';
import { HlmSkeletonModule } from '@spartan-ng/ui-skeleton-helm';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { HlmLabelDirective } from '@spartan-ng/ui-label-helm';
import { HlmSwitchComponent } from '@spartan-ng/ui-switch-helm';
import { HlmIconComponent } from '@spartan-ng/ui-icon-helm';
import { lucideCheck, lucideX } from '@ng-icons/lucide';
import { provideIcons } from '@ng-icons/core';

@Component({
  selector: 'app-workspace-rules-table',
  standalone: true,
  imports: [
    FormsModule,
    ReactiveFormsModule,
    HlmMenuModule,
    BrnTableModule,
    HlmTableModule,
    HlmButtonModule,
    HlmInputDirective,
    BrnSelectModule,
    HlmSelectModule,
    HlmSkeletonModule,
    HlmCardModule,
    HlmPopoverModule,
    BrnPopoverComponent,
    BrnPopoverTriggerDirective,
    HlmLabelDirective,
    BrnPopoverImports,
    HlmSwitchComponent,
    HlmIconComponent
  ],
  templateUrl: './rules-table.component.html',
  providers: [provideIcons({ lucideCheck, lucideX })],
  styles: ``
})
export class WorkspaceRulesTableComponent {
  protected ruleService = inject(BadPracticeRuleService);
  protected queryClient = injectQueryClient();

  availableRepos = input<string[]>();

  _newTitle = new FormControl('');
  _newDescription = new FormControl('');
  _newConditions = new FormControl('');
  _newIsActive = new FormControl(true);

  _newEditTitle = new FormControl('');
  _newEditDescription = new FormControl('');
  _newEditConditions = new FormControl('');
  _newEditIsActive = new FormControl(true);

  protected readonly _trackBy: TrackByFunction<PullRequestBadPracticeRule> = (_: number, r: PullRequestBadPracticeRule) => r.id;
  protected _selectedRule = signal<PullRequestBadPracticeRule | undefined>(undefined);
  protected readonly _selectedRepo = signal<string | undefined>(undefined);
  protected readonly repoOwner = computed(() => this._selectedRepo()?.split('/')[0] ?? '');
  protected readonly repoName = computed(() => this._selectedRepo()?.split('/')[1] ?? '');
  protected readonly rules = computed(() => this.getRulesForRepoQuery.data() ?? []);

  resetCreateForm() {
    this._newTitle.setValue('');
    this._newDescription.setValue('');
    this._newConditions.setValue('');
    this._newIsActive.setValue(true);
  }

  resetEditForm() {
    this._newEditTitle.setValue('');
    this._newEditDescription.setValue('');
    this._newEditConditions.setValue('');
    this._newEditIsActive.setValue(true);
  }

  selectRule(selectedRule: PullRequestBadPracticeRule) {
    this._selectedRule.set(selectedRule);
    this._newEditTitle.setValue(selectedRule.title ?? '');
    this._newEditDescription.setValue(selectedRule.description ?? '');
    this._newEditConditions.setValue(selectedRule.conditions ?? '');
    this._newEditIsActive.setValue(selectedRule.active ?? true);
  }

  cancelEdit() {
    this._selectedRule.set(undefined);
    this.resetEditForm();
  }

  save() {
    this.saveRule.mutate();
  }

  saveRule = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(
        this.ruleService.updateRule(this._selectedRule()?.id ?? 0, {
          title: this._newEditTitle.value,
          description: this._newEditDescription.value,
          conditions: this._newEditConditions.value,
          active: this._newEditIsActive.value
        } as PullRequestBadPracticeRule)
      ),
    queryKey: ['workspace', 'rules', 'update'],
    onSettled: () => this.invalidateRepoQuery()
  }));

  createRule = injectMutation(() => ({
    mutationFn: () =>
      lastValueFrom(
        this.ruleService.createRule(this.repoOwner(), this.repoName(), {
          title: this._newTitle.value,
          description: this._newDescription.value,
          conditions: this._newConditions.value,
          active: true
        } as PullRequestBadPracticeRule)
      ),
    queryKey: ['workspace', 'rules', 'create'],
    onSettled: () => this.invalidateRepoQuery()
  }));

  getRulesForRepoQuery = injectQuery(() => ({
    queryFn: () => lastValueFrom(this.ruleService.getRulesByRepository(this.repoOwner(), this.repoName())),
    queryKey: ['activity', 'rules', this._selectedRepo()]
  }));

  protected invalidateRepoQuery() {
    this.queryClient.invalidateQueries({ queryKey: ['activity', 'rules'] });
  }
}
