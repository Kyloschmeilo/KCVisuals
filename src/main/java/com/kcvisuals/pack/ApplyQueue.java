package com.kcvisuals.pack;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Runs at most one job at a time with "latest wins" coalescing. A submit while a job is in flight replaces the single
 * pending item; when the running job's future completes, only the newest pending item starts. Every submit returns a
 * future that completes with the result of its own run or of the run that superseded it.
 */
public final class ApplyQueue<T, R> {
    private final Function<T, CompletableFuture<R>> runner;

    private boolean running;
    private T pending;
    private CompletableFuture<R> pendingResult;

    /** {@code runner} starts a job and returns a future that completes when the job (including any reload) is done. */
    public ApplyQueue(Function<T, CompletableFuture<R>> runner) {
        this.runner = runner;
    }

    public CompletableFuture<R> submit(T item) {
        CompletableFuture<R> result;
        synchronized (this) {
            if (running) {
                if (pendingResult == null) {
                    pendingResult = new CompletableFuture<>();
                }
                pending = item;
                return pendingResult;
            }
            running = true;
            result = new CompletableFuture<>();
        }
        start(item, result);
        return result;
    }

    private void start(T item, CompletableFuture<R> result) {
        CompletableFuture<R> job;
        try {
            job = runner.apply(item);
        } catch (RuntimeException | Error e) {
            job = CompletableFuture.failedFuture(e);
        }
        job.whenComplete((r, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
            } else {
                result.complete(r);
            }
            startNext();
        });
    }

    private void startNext() {
        T item;
        CompletableFuture<R> result;
        synchronized (this) {
            if (pendingResult == null) {
                running = false;
                return;
            }
            item = pending;
            result = pendingResult;
            pending = null;
            pendingResult = null;
        }
        start(item, result);
    }
}
