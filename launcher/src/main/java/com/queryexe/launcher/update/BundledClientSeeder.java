package com.queryexe.launcher.update;

import com.queryexe.launcher.Launcher;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;

/**
 * First-run seeding of the client jar bundled inside the installer.
 *
 * <p>The exe installer/AppImage ships {@code query-exe-client-seed.jar} next to
 * the launcher jar in the app directory. If {@code %APPDATA%/QueryExe/bin/query-exe.jar}
 * does not exist yet — i.e. a fresh install — the seed is copied there so the
 * first start works offline and skips a full download. The jar carries its own
 * version in its embedded {@code app.properties}, so no version marker is
 * needed. From then on the normal {@link UpdateManager} flow owns the jar and
 * replaces it with whatever GitHub serves.
 *
 * <p>In dev runs (classes dir instead of a jar, no seed file) this is a no-op.
 */
@Slf4j
public final class BundledClientSeeder {

    private BundledClientSeeder() {
    }

    /** Best-effort: on any failure the launcher just downloads from GitHub as usual. */
    public static void seedIfMissing() {
        try {
            Path clientJar = Launcher.getClientJar();
            if (Files.exists(clientJar)) return;

            Path appDir = launcherJarDirectory();
            if (appDir == null) return;
            Path seedJar = appDir.resolve("query-exe-client-seed.jar");
            if (!Files.exists(seedJar)) return;

            Files.createDirectories(clientJar.getParent());
            // Copy via a temp name so a crash mid-copy never leaves a broken query-exe.jar.
            Path tmp = clientJar.resolveSibling("query-exe.jar.seed");
            Files.copy(seedJar, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, clientJar, StandardCopyOption.REPLACE_EXISTING);
            log.info("Seeded bundled client jar {} -> {}", seedJar, clientJar);
        } catch (Exception e) {
            log.warn("Could not seed bundled client jar: {}", e.toString());
        }
    }

    /** Directory containing the running launcher jar, or null in dev runs. */
    private static Path launcherJarDirectory() throws Exception {
        CodeSource source = Launcher.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) return null;
        Path location = Path.of(source.getLocation().toURI());
        return Files.isRegularFile(location) ? location.getParent() : null;
    }
}
