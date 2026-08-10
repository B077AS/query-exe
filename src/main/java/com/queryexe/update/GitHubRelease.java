package com.queryexe.update;

import lombok.Data;

import java.util.List;

/**
 * Mirror of the subset of GitHub's {@code GET /repos/{owner}/{repo}/releases/latest}
 * response {@link LauncherUpdateService} needs — there is no hub, so the
 * query-exe-launcher repo is queried directly against api.github.com.
 */
@Data
public class GitHubRelease {
    private String tag_name;
    private List<GitHubAsset> assets;

    /** {@code tag_name} with a leading "v" tolerated and stripped. */
    public String version() {
        if (tag_name == null) return null;
        return tag_name.startsWith("v") || tag_name.startsWith("V") ? tag_name.substring(1) : tag_name;
    }

    /** First asset with this exact name, or null. */
    public GitHubAsset findAssetExact(String name) {
        if (assets == null) return null;
        return assets.stream()
                .filter(a -> name.equals(a.getName()))
                .findFirst()
                .orElse(null);
    }
}
