package io.github.b077as.queryexe.launcher;

/** The steps the page shows, in order. The names are what goes over the bridge ({@code phase.name().toLowerCase()}). */
public enum Phase {
    CHECKING,
    DOWNLOADING,
    INSTALLING,
    STARTING
}
