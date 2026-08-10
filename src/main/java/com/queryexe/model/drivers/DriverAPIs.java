package com.queryexe.model.drivers;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.queryexe.model.connections.ConnectionTypes;
import com.queryexe.service.Async;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DriverAPIs {

    private static final String MAVEN_METADATA_BASE = "https://repo1.maven.org/maven2";
    private static final OkHttpClient client = new OkHttpClient();


    public static void getAllDriversForConnectionTypeAsync(
            ConnectionTypes connectionType,
            Consumer<List<DriverInfo>> onSuccess,
            Consumer<Exception> onError) {

        Async.run(() -> {
            try {
                List<DriverInfo> result = fetchAllDrivers(connectionType);
                onSuccess.accept(result);
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    public static void downloadDriverAsync(
            DriverInfo driver,
            File targetFile,
            Runnable onSuccess,
            Consumer<Exception> onError) {

        Async.run(() -> {
            try {
                downloadDriverInternal(driver, targetFile);
                onSuccess.run();
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    private static void downloadDriverInternal(DriverInfo driver, File targetFile) throws Exception {
        // Create parent directory if it doesn't exist
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        Request request = new Request.Builder()
                .url(driver.getDownloadUrl())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Download failed with status: " + response.code());
            }

            Files.write(targetFile.toPath(), response.body().bytes());
        }
    }

    private static List<DriverInfo> fetchAllDrivers(ConnectionTypes connectionType) throws Exception {
        String groupPath = connectionType.getMavenGroupId().replace('.', '/');
        String metadataUrl = String.format("%s/%s/%s/maven-metadata.xml",
                MAVEN_METADATA_BASE, groupPath, connectionType.getMavenArtifactId());

        Request request = new Request.Builder()
                .url(metadataUrl)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Maven metadata returned status: " + response.code());
            }

            String xml = response.body().string();
            return parseMavenMetadata(xml, connectionType);
        }
    }

    private static List<DriverInfo> parseMavenMetadata(String xml, ConnectionTypes connectionType) throws Exception {
        List<DriverInfo> drivers = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        // Get all version elements
        NodeList versionNodes = doc.getElementsByTagName("version");

        for (int i = 0; i < versionNodes.getLength(); i++) {
            String version = versionNodes.item(i).getTextContent().trim();

            // Filter out non-stable versions
            if (version.contains("SNAPSHOT") || version.contains("alpha") ||
                    version.contains("beta") || version.contains("RC") ||
                    version.contains("M") || version.contains("rc")) {
                continue;
            }

            String groupPath = connectionType.getMavenGroupId().replace('.', '/');
            String downloadUrl = String.format(
                    "https://repo1.maven.org/maven2/%s/%s/%s/%s-%s.jar",
                    groupPath, connectionType.getMavenArtifactId(), version,
                    connectionType.getMavenArtifactId(), version
            );

            String fileName = String.format("%s-%s.jar", connectionType.getMavenArtifactId(), version);

            drivers.add(new DriverInfo(
                    connectionType.getDefaultDriverInfo().getName(),
                    version,
                    connectionType.getDefaultDriverInfo().getDriverClass(),
                    downloadUrl,
                    fileName
            ));
        }

        // Sort versions in descending order (newest first)
        drivers.sort(new VersionComparator().reversed());

        return drivers;
    }

    /**
     * Comparator for semantic versioning
     */
    private static class VersionComparator implements Comparator<DriverInfo> {
        private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:\\.(\\d+))?(.*)");

        @Override
        public int compare(DriverInfo d1, DriverInfo d2) {
            return compareVersions(d1.getVersion(), d2.getVersion());
        }

        private int compareVersions(String v1, String v2) {
            Matcher m1 = VERSION_PATTERN.matcher(v1);
            Matcher m2 = VERSION_PATTERN.matcher(v2);

            if (!m1.matches() || !m2.matches()) {
                // Fallback to string comparison if version doesn't match pattern
                return v1.compareTo(v2);
            }

            // Compare major
            int major1 = Integer.parseInt(m1.group(1));
            int major2 = Integer.parseInt(m2.group(1));
            if (major1 != major2) return Integer.compare(major1, major2);

            // Compare minor
            int minor1 = Integer.parseInt(m1.group(2));
            int minor2 = Integer.parseInt(m2.group(2));
            if (minor1 != minor2) return Integer.compare(minor1, minor2);

            // Compare patch
            int patch1 = Integer.parseInt(m1.group(3));
            int patch2 = Integer.parseInt(m2.group(3));
            if (patch1 != patch2) return Integer.compare(patch1, patch2);

            // Compare build number (group 4, optional)
            String build1 = m1.group(4);
            String build2 = m2.group(4);
            if (build1 != null && build2 != null) {
                int b1 = Integer.parseInt(build1);
                int b2 = Integer.parseInt(build2);
                if (b1 != b2) return Integer.compare(b1, b2);
            } else if (build1 != null) {
                return 1; // v1 has build number, v2 doesn't
            } else if (build2 != null) {
                return -1; // v2 has build number, v1 doesn't
            }

            // Compare suffix (like "jre11")
            String suffix1 = m1.group(5);
            String suffix2 = m2.group(5);
            if (suffix1.isEmpty() && suffix2.isEmpty()) return 0;
            if (suffix1.isEmpty()) return 1; // No suffix is "greater" than suffix
            if (suffix2.isEmpty()) return -1;

            return suffix1.compareTo(suffix2);
        }
    }
}