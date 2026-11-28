package io.github.b077as.queryexe.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class PageLifecycleTest {

    private static final Duration SHORT = Duration.ofMillis(100);
    private static final Duration LONG = Duration.ofSeconds(30);

    private final List<Long> asked = new ArrayList<>();

    private PageLifecycle lifecycle(Duration acknowledgeWithin) {
        return new PageLifecycle((name, data) -> {
            assertEquals(PageLifecycle.CLOSE_REQUESTED, name);
            asked.add((Long) ((Map<?, ?>) data).get("id"));
        }, false, acknowledgeWithin);
    }

    private static CompletableFuture<Boolean> ask(PageLifecycle page) {
        return page.askToClose().toCompletableFuture();
    }

    @Test
    void closesAtOnceWhileThereIsNoPageToAsk() throws Exception {
        PageLifecycle page = lifecycle(LONG);
        assertTrue(ask(page).get(1, TimeUnit.SECONDS));
        page.attach();
        assertTrue(ask(page).get(1, TimeUnit.SECONDS), "attached but not ready yet");
        assertTrue(asked.isEmpty());
    }

    @Test
    void thePageDecides() throws Exception {
        PageLifecycle page = lifecycle(LONG);
        page.ready();

        CompletableFuture<Boolean> first = ask(page);
        assertFalse(first.isDone());
        page.acknowledge(asked.getLast());
        page.answer(asked.getLast(), false);
        assertFalse(first.get(1, TimeUnit.SECONDS));

        CompletableFuture<Boolean> second = ask(page);
        page.answer(asked.getFirst(), true); // an old question's answer
        assertFalse(second.isDone());
        page.answer(asked.getLast(), true);
        assertTrue(second.get(1, TimeUnit.SECONDS));
    }

    @Test
    void aPageThatDoesNotRespondCannotKeepTheWindowOpen() throws Exception {
        PageLifecycle page = lifecycle(SHORT);
        page.ready();
        assertTrue(ask(page).get(5, TimeUnit.SECONDS));
    }

    @Test
    void aPageThatHeardTheQuestionMayTakeItsTime() throws Exception {
        PageLifecycle page = lifecycle(SHORT);
        page.ready();
        CompletableFuture<Boolean> decision = ask(page);
        page.acknowledge(asked.getLast());
        Thread.sleep(SHORT.multipliedBy(4));
        assertFalse(decision.isDone());
        page.answer(asked.getLast(), true);
        assertTrue(decision.get(1, TimeUnit.SECONDS));
    }

    @Test
    void aPageThatGoesAwayDropsItsQuestion() throws Exception {
        PageLifecycle page = lifecycle(LONG);
        page.ready();
        CompletableFuture<Boolean> decision = ask(page);
        page.gone();
        assertFalse(decision.get(1, TimeUnit.SECONDS));
        assertTrue(ask(page).get(1, TimeUnit.SECONDS), "nothing to ask until a page is ready again");
    }

    @Test
    void saysWhetherAPageReplacesAnEarlierOne() {
        PageLifecycle page = lifecycle(LONG);
        assertFalse(page.attach());
        assertTrue(page.attach());
        assertTrue(new PageLifecycle((name, data) -> { }, true).attach(), "a window made again");
    }
}
