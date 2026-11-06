package com.queryexe.launcher.update;

import lombok.Getter;

/**
 * The phases the launcher steps through, each with the Discord-style phrase shown
 * under the logo. {@code DOWNLOADING} is the only determinate phase (it reports a
 * 0..1 fraction); every other phase drives the progress bar in indeterminate mode.
 */
@Getter
public enum Phase {
    CHECKING("Checking for updates…", false),
    DOWNLOADING("Downloading update…", true),
    INSTALLING("Installing update…", false),
    STARTING("Starting QueryExe…", false),
    UP_TO_DATE("You're up to date", false),
    DONE("Ready", false),
    ERROR("Something went wrong", false);

    private final String phrase;
    /** True when this phase reports a real 0..1 progress fraction. */
    private final boolean determinate;

    Phase(String phrase, boolean determinate) {
        this.phrase = phrase;
        this.determinate = determinate;
    }
}
