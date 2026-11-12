package io.github.b077as.queryexe.app;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the Windows "Apps &amp; features" entry (Control Panel, Programs and Features) at the version that is really installed.
 *
 * <p>The Inno Setup installer writes {@code DisplayVersion} once, when it runs. Updates are swapped in by the jpack stub and never
 * run the installer again, so without this the list would show the first version forever. The client runs after every swap, so
 * it corrects that one value at start. Nothing else Inno Setup owns (the uninstaller, the file list, the name) is touched.
 *
 * <p>Best effort and silent. Only a copy started by a jpack stub takes part (the stub sets {@code JPACK_VERSION}): an IDE run or a
 * hand-started jar would otherwise write its own version over the installed one. A machine without the Inno Setup key (a portable
 * copy) is skipped, so this never invents an "installed program" entry. The AppImage has no such list.
 */
@Slf4j
public final class AppsFeatures {

    /** Must match {@code AppId} in packaging/windows/queryexe.iss: Inno Setup names the key {@code <AppId>_is1}. The installer is per user, so it is under HKCU. */
    private static final String UNINSTALL_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{5D2A7C41-8E3B-4F6A-B9D0-1C7E4A8F2B35}_is1";

    private AppsFeatures() {
    }

    /** Never throws; a failure is only logged at debug level. */
    public static void syncVersion() {
        String version = System.getenv("JPACK_VERSION");
        if (!System.getProperty("os.name", "").toLowerCase().contains("win") || version == null || version.isBlank()) {
            return;
        }
        try {
            if (reg("query", UNINSTALL_KEY, "/v", "DisplayVersion") != 0) {
                log.debug("No Inno Setup uninstall entry: not an installed copy, leaving Apps & features alone");
                return;
            }
            reg("add", UNINSTALL_KEY, "/v", "DisplayVersion", "/t", "REG_SZ", "/d", version, "/f");
            log.info("Apps & features now shows version {}", version);
        } catch (Exception e) {
            log.debug("Could not update the Apps & features version: {}", e.toString());
        }
    }

    private static int reg(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("reg");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("reg " + arguments[0] + " timed out");
        }
        return process.exitValue();
    }
}
