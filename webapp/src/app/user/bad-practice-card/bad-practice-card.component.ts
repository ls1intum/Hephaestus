import { Component, computed, inject, input, signal } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { octCheck, octX } from '@ng-icons/octicons';
import { ActivityService, BadPracticeFeedback } from '@app/core/modules/openapi';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { lastValueFrom } from 'rxjs';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmMenuComponent, HlmMenuItemImports, HlmMenuStructureImports } from '@spartan-ng/ui-menu-helm';
import { BrnMenuImports } from '@spartan-ng/brain/menu';
import { HlmLabelDirective } from '@spartan-ng/ui-label-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { lucideEllipsis } from '@ng-icons/lucide';
import { HlmDialogImports } from '@spartan-ng/ui-dialog-helm';
import { BrnDialogImports } from '@spartan-ng/brain/dialog';
import { BrnSelectImports } from '@spartan-ng/brain/select';
import { HlmSelectImports } from '@spartan-ng/ui-select-helm';

@Component({
  selector: 'app-bad-practice-card',
  imports: [
    HlmCardModule,
    NgIcon,
    HlmSelectImports,
    HlmButtonDirective,
    BrnMenuImports,
    HlmMenuComponent,
    HlmMenuItemImports,
    HlmMenuStructureImports,
    HlmDialogImports,
    BrnDialogImports,
    HlmLabelDirective,
    BrnSelectImports,
    HlmInputDirective,
    ReactiveFormsModule,
    FormsModule
  ],
  templateUrl: './bad-practice-card.component.html',
  styles: ``,
  providers: [provideIcons({ lucideEllipsis })]
})
export class BadPracticeCardComponent {
  activityService = inject(ActivityService);
  queryClient = inject(QueryClient);

  protected readonly octCheck = octCheck;
  protected readonly octX = octX;

  id = input.required<number>();
  title = input.required<string>();
  description = input.required<string>();
  resolved = input<boolean>();
  userResolved = input<boolean>();

  _newExplanation = new FormControl('');
  _selectedType = signal<string | undefined>(undefined);
  allFeedbackTypes = computed(() => ['Not a bad practice', 'Irrelevant', 'Incorrect', 'Imprecise', 'Other']);

  resolveBadPracticeMutation = injectMutation(() => ({
    mutationFn: (badPracticeId: number) => lastValueFrom(this.activityService.resolveBadPractice(badPracticeId)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['activity'] });
    }
  }));

  resolveBadPractice(badPracticeId: number): void {
    this.resolveBadPracticeMutation.mutate(badPracticeId);
  }

  feedbackForBadPracticeMutation = injectMutation(() => ({
    mutationFn: ({ badPracticeId, feedBack }: { badPracticeId: number; feedBack: BadPracticeFeedback }) =>
      lastValueFrom(this.activityService.provideFeedbackForBadPractice(badPracticeId, feedBack))
  }));

  provideFeedbackForBadPractice(): void {
    const type = this._selectedType() ?? 'Other';
    const explanation = this._newExplanation.value ?? '';
    const feedBack: BadPracticeFeedback = { type, explanation };
    const badPracticeId = this.id();
    this.feedbackForBadPracticeMutation.mutate({ badPracticeId, feedBack });
  }
}
