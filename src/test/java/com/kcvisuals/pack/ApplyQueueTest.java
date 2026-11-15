package com.kcvisuals.pack;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ApplyQueueTest {
    /** Records every run and hands back a future the test completes by hand. */
    static final class FakeRunner {
        final List<String> started = new ArrayList<>();
        final Map<String, CompletableFuture<String>> futures = new HashMap<>();

        CompletableFuture<String> run(String item) {
            started.add(item);
            CompletableFuture<String> f = new CompletableFuture<>();
            futures.put(item, f);
            return f;
        }
    }

    @Test
    void threeRapidSubmitsRunFirstThenOnlyLast() {
        FakeRunner runner = new FakeRunner();
        ApplyQueue<String, String> q = new ApplyQueue<>(runner::run);
        CompletableFuture<String> a = q.submit("a");
        CompletableFuture<String> b = q.submit("b");
        CompletableFuture<String> c = q.submit("c");
        assertEquals(List.of("a"), runner.started, "nothing else may start while the first is in flight");

        runner.futures.get("a").complete("done-a");
        assertEquals("done-a", a.join());
        assertEquals(List.of("a", "c"), runner.started, "middle submit must be dropped");
        assertFalse(b.isDone());

        runner.futures.get("c").complete("done-c");
        assertEquals("done-c", b.join(), "a superseded submit completes with the superseding run");
        assertEquals("done-c", c.join());
        assertEquals(List.of("a", "c"), runner.started);
    }

    @Test
    void idleQueueStartsImmediatelyAgain() {
        FakeRunner runner = new FakeRunner();
        ApplyQueue<String, String> q = new ApplyQueue<>(runner::run);
        q.submit("a");
        runner.futures.get("a").complete("x");
        q.submit("b");
        assertEquals(List.of("a", "b"), runner.started);
    }

    @Test
    void failureIsReportedAndQueueContinues() {
        FakeRunner runner = new FakeRunner();
        ApplyQueue<String, String> q = new ApplyQueue<>(runner::run);
        CompletableFuture<String> a = q.submit("a");
        CompletableFuture<String> b = q.submit("b");
        runner.futures.get("a").completeExceptionally(new IllegalStateException("boom"));
        assertTrue(a.isCompletedExceptionally());
        assertEquals(List.of("a", "b"), runner.started);
        runner.futures.get("b").complete("ok");
        assertEquals("ok", b.join());
    }

    @Test
    void runnerThrowingSynchronouslyDoesNotWedgeQueue() {
        ApplyQueue<String, String> q = new ApplyQueue<>(s -> {
            if (s.equals("bad")) {
                throw new IllegalStateException("sync");
            }
            return CompletableFuture.completedFuture(s);
        });
        assertTrue(q.submit("bad").isCompletedExceptionally());
        assertEquals("good", q.submit("good").join());
    }
}
