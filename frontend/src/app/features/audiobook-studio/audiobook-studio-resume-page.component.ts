import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AudiobookStudioWorkspaceComponent } from './audiobook-studio-page.component';
import { AudiobookWorkflowSnapshotResponse } from '../../shared/api-contract.generated';
import { AudiobookWorkflowService } from '../audiobook-shared/service/audiobook-workflow.service';

@Component({
  selector: 'app-audiobook-studio-resume-page',
  standalone: true,
  imports: [CommonModule, AudiobookStudioWorkspaceComponent],
  template: `
    <section class="studio-resume-page">
      <p *ngIf="error" class="error" role="alert">{{ error }}</p>
      <section *ngIf="loading" class="app-panel" role="status">Loading audiobook project...</section>
      <app-audiobook-studio-workspace
        *ngIf="!loading && snapshot"
        [showHero]="false"
        [snapshot]="snapshot"
      ></app-audiobook-studio-workspace>
    </section>
  `
})
export class AudiobookStudioResumePageComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly audiobookWorkflowService = inject(AudiobookWorkflowService);
  private loadRequestId = 0;

  loading = true;
  error: string | null = null;
  snapshot: AudiobookWorkflowSnapshotResponse | null = null;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const projectId = params.get('projectId');
      if (!projectId) {
        this.snapshot = null;
        this.error = 'The audiobook project could not be loaded.';
        this.loading = false;
        return;
      }

      this.loading = true;
      this.error = null;
      this.snapshot = null;
      void this.loadSnapshot(projectId);
    });
  }

  private async loadSnapshot(projectId: string): Promise<void> {
    const requestId = ++this.loadRequestId;
    const isCurrentRequest = () => requestId === this.loadRequestId;
    try {
      const snapshot = await this.audiobookWorkflowService.getProjectSnapshot(projectId);
      if (isCurrentRequest()) {
        this.snapshot = snapshot;
      }
    } catch (error) {
      if (isCurrentRequest()) {
        this.snapshot = null;
        this.error = error instanceof Error ? error.message : 'The audiobook project could not be loaded.';
      }
    } finally {
      if (isCurrentRequest()) {
        this.loading = false;
      }
    }
  }
}
