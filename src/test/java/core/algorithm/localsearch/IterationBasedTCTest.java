package core.algorithm.localsearch;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IterationBasedTCTest {

    @Test
    void allowsExactlyLimitIterations() {
        IterationBasedTC tc = new IterationBasedTC(3);
        assertFalse(tc.isSatisfied(null, null));
        assertFalse(tc.isSatisfied(null, null));
        assertFalse(tc.isSatisfied(null, null));
        assertTrue(tc.isSatisfied(null, null));
        assertTrue(tc.isSatisfied(null, null));

        tc.init();
        assertFalse(tc.isSatisfied(null, null));
    }

    @Test
    void countsCorrectlyUnderConcurrency() throws InterruptedException {
        IterationBasedTC tc = new IterationBasedTC(10_000);
        AtomicInteger granted = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int t = 0; t < 8; t++) {
                pool.submit(() -> {
                    while (!tc.isSatisfied(null, null)) granted.incrementAndGet();
                });
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
        assertEquals(10_000, granted.get());
    }
}
