package io.github.b077as.queryexe.app;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import lombok.extern.slf4j.Slf4j;

/**
 * What Java knows about the page that is in the window: whether it replaced an earlier one (after a crash or a reload), and
 * whether it can be asked before the window closes.
 *
 * <p>Closing: only the page knows what would be lost (unsaved queries, unapplied grid edits, open transactions), so a close
 * request is sent to it as {@value #CLOSE_REQUESTED}. It confirms at once that it heard ({@link #acknowledge}) and answers
 * once the user has decided ({@link #answer}). A page that is not there, or does not confirm in time, cannot keep the window
 * open.
 */
@Slf4j
public class PageLifecycle {

    public static final String CLOSE_REQUESTED = "window.closeRequested";

    /** How long the page has to confirm a close request; generous, because a busy page must not lose work to it. */
    private static final Duration ACKNOWLEDGE_WITHIN = Duration.ofSeconds(5);

    private final BiConsumer<String, Object> events;
    private final Duration acknowledgeWithin;
    private boolean attached;
    private boolean listening;
    private long questions;
    private Question open;

    private static final class Question {
        long id;
        CompletableFuture<Boolean> allowed = new CompletableFuture<>();
        boolean acknowledged;

        Question(long id) {
            this.id = id;
        }
    }

    /** @param replacesEarlierPage true when the very first page already follows another one (a window made again) */
    public PageLifecycle(BiConsumer<String, Object> events, boolean replacesEarlierPage) {
        this(events, replacesEarlierPage, ACKNOWLEDGE_WITHIN);
    }

    PageLifecycle(BiConsumer<String, Object> events, boolean replacesEarlierPage, Duration acknowledgeWithin) {
        this.events = events;
        this.attached = replacesEarlierPage;
        this.acknowledgeWithin = acknowledgeWithin;
    }

    /**
     * A page has started. Returns whether an earlier page was there before it, in which case what Java holds for that page is
     * stale. The new page is not asked about closing until it says it is {@link #ready}.
     */
    public synchronized boolean attach() {
        boolean replaced = attached;
        attached = true;
        gone();
        return replaced;
    }

    /** The page is on screen and listens for close requests. */
    public synchronized void ready() {
        listening = true;
    }

    /** The page crashed, failed to load or is being replaced: a question it was asked is dropped and the window stays. */
    public synchronized void gone() {
        listening = false;
        if (open != null) {
            Question question = open;
            open = null;
            question.allowed.complete(false);
        }
    }

    /** The window was asked to close. Completes with whether it may; at once when there is no page to ask. */
    public CompletionStage<Boolean> askToClose() {
        Question question;
        synchronized (this) {
            if (!listening) {
                return CompletableFuture.completedFuture(true);
            }
            if (open != null) {
                return open.allowed;
            }
            question = new Question(++questions);
            open = question;
        }
        events.accept(CLOSE_REQUESTED, Map.of("id", question.id));
        CompletableFuture.delayedExecutor(acknowledgeWithin.toMillis(), TimeUnit.MILLISECONDS).execute(() -> giveUp(question));
        return question.allowed;
    }

    /** The page heard the question and is deciding: it may take as long as the user needs. */
    public synchronized void acknowledge(long id) {
        if (open != null && open.id == id) {
            open.acknowledged = true;
        }
    }

    /** The user's decision. An answer to a question that is no longer open is ignored. */
    public synchronized void answer(long id, boolean close) {
        if (open != null && open.id == id) {
            Question question = open;
            open = null;
            question.allowed.complete(close);
        }
    }

    private synchronized void giveUp(Question question) {
        if (open == question && !question.acknowledged) {
            log.warn("The page did not respond to a close request; closing the window");
            open = null;
            listening = false;
            question.allowed.complete(true);
        }
    }
}
