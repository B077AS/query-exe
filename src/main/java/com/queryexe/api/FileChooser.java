package com.queryexe.api;

import java.nio.file.Path;
import java.util.Optional;

/** The system file dialogs, owned by the window. Each call waits for the user, so it must not run on the UI thread. */
public interface FileChooser {

    Optional<Path> open(String title, String filterName, String... extensions);

    Optional<Path> save(String title, String suggestedName, String filterName, String... extensions);
}
