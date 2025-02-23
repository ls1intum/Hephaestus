import { Injectable, OnDestroy } from '@angular/core';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { catchError, EMPTY, Observable, Subject, timer } from 'rxjs';
import { retryWhen, delay, takeUntil } from 'rxjs/operators';
import { AIInsight, ReviewMetrics } from '../models/analytics.model';

export interface AnalyticsUpdate {
  type: 'metrics' | 'insight';
  data: ReviewMetrics | AIInsight;
  timestamp: string;
}

@Injectable({
  providedIn: 'root'
})
export class AnalyticsWebSocketService implements OnDestroy {
  private readonly WS_ENDPOINT = 'ws://localhost:8080/ws/analytics';
  private readonly RECONNECT_INTERVAL = 5000;

  private ws$?: WebSocketSubject<AnalyticsUpdate>;
  private destroy$ = new Subject<void>();
  private reconnection$ = new Subject<void>();

  constructor() {}

  connect(): Observable<AnalyticsUpdate> {
    if (!this.ws$ || this.ws$.closed) {
      this.ws$ = this.createWebSocket();
    }
    
    return this.ws$.pipe(
      catchError(error => {
        console.error('WebSocket error:', error);
        return EMPTY;
      }),
      retryWhen(errors =>
        errors.pipe(
          delay(this.RECONNECT_INTERVAL),
          takeUntil(this.destroy$)
        )
      )
    );
  }

  private createWebSocket(): WebSocketSubject<AnalyticsUpdate> {
    return webSocket({
      url: this.WS_ENDPOINT,
      openObserver: {
        next: () => {
          console.log('WebSocket connected');
        }
      },
      closeObserver: {
        next: () => {
          console.log('WebSocket disconnected');
          this.reconnection$.next();
        }
      }
    });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    this.ws$?.complete();
  }
}
