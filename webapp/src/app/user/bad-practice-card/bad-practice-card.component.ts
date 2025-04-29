import { Component, computed, inject, input, signal } from '@angular/core';
import { HlmCardModule } from '@spartan-ng/ui-card-helm';
import { NgIconComponent, provideIcons } from '@ng-icons/core';
import { ActivityService, BadPracticeFeedback } from '@app/core/modules/openapi';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { lastValueFrom } from 'rxjs';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmMenuComponent, HlmMenuItemImports, HlmMenuStructureImports } from '@spartan-ng/ui-menu-helm';
import { BrnMenuImports } from '@spartan-ng/brain/menu';
import { HlmLabelDirective } from '@spartan-ng/ui-label-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { lucideRocket, lucideCheck, lucideFlame, lucideTriangleAlert, lucideOctagonX, lucideBan, lucideCircleHelp, lucideBug } from '@ng-icons/lucide';
import { HlmDialogImports } from '@spartan-ng/ui-dialog-helm';
import { BrnDialogImports } from '@spartan-ng/brain/dialog';
import { BrnSelectImports } from '@spartan-ng/brain/select';
import { HlmSelectImports } from '@spartan-ng/ui-select-helm';
import { PullRequestBadPractice } from '@app/core/modules/openapi';
import { stateConfig } from '@app/utils';
import { HlmTooltipComponent, HlmTooltipImports } from '@spartan-ng/ui-tooltip-helm';
import { BrnTooltipImports } from '@spartan-ng/brain/tooltip';

@Component({
  selector: 'app-bad-practice-card',
  imports: [
    HlmCardModule,
    NgIconComponent,
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
    FormsModule,
    HlmCardModule,
    HlmTooltipComponent,
    HlmTooltipImports,
    BrnTooltipImports
  ],
  templateUrl: './bad-practice-card.component.html',
  providers: [provideIcons({ lucideRocket, lucideCheck, lucideFlame, lucideTriangleAlert, lucideBug, lucideOctagonX, lucideBan, lucideCircleHelp })]
})
export class BadPracticeCardComponent {
  activityService = inject(ActivityService);
  queryClient = inject(QueryClient);

  title = input.required<string>();
  description = input.required<string>();
  state = input.required<PullRequestBadPractice.StateEnum>();
  id = input.required<number>();
  currUserIsDashboardUser = input<boolean>(false);

  icon = computed(() => stateConfig[this.state()].icon);
  text = computed(() => stateConfig[this.state()].text);
  color = computed(() => stateConfig[this.state()].color);

  _newExplanation = new FormControl('');
  _selectedType = signal<string | undefined>(undefined);
  allFeedbackTypes = computed(() => ['Not a bad practice', 'Irrelevant', 'Incorrect', 'Imprecise', 'Other']);

  resolveBadPracticeMutation = injectMutation(() => ({
    mutationFn: ({ badPracticeId, state }: { badPracticeId: number; state: PullRequestBadPractice.StateEnum }) =>
      lastValueFrom(this.activityService.resolveBadPractice(badPracticeId, state)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['activity'] });
    }
  }));

  resolveBadPracticeAsFixed(badPracticeId: number): void {
    const state = PullRequestBadPractice.StateEnum.Fixed;
    this.resolveBadPracticeMutation.mutate({ badPracticeId, state });
  }

  resolveBadPracticeAsWontFixed(badPracticeId: number): void {
    const state = PullRequestBadPractice.StateEnum.WontFix;
    this.resolveBadPracticeMutation.mutate({ badPracticeId, state });
  }

  resolveBadPracticeAsWrong(badPracticeId: number): void {
    const state = PullRequestBadPractice.StateEnum.Wrong;
    this.resolveBadPracticeMutation.mutate({ badPracticeId, state });
  }

  feedbackForBadPracticeMutation = injectMutation(() => ({
    mutationFn: ({ badPracticeId, feedBack }: { badPracticeId: number; feedBack: BadPracticeFeedback }) =>
      lastValueFrom(this.activityService.provideFeedbackForBadPractice(badPracticeId, feedBack)),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: ['activity'] });
    }
  }));

  provideFeedbackForBadPractice(): void {
    const type = this._selectedType() ?? 'Other';
    const explanation = this._newExplanation.value ?? '';
    const feedBack: BadPracticeFeedback = { type, explanation };
    const badPracticeId = this.id();
    this.feedbackForBadPracticeMutation.mutate({ badPracticeId, feedBack });
  }
}
