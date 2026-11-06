package com.queryexe.launcher.update;

import lombok.Data;

import java.util.List;

/**
 * Mirror of the subset of GitHub's {@code GET /repos/{owner}/{repo}/releases/latest}
 * response this launcher needs — there is no hub, so this is queried directly
 * against api.github.com instead of a custom endpoint.
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

    /** First asset whose name matches the given regex, or null. */
    public GitHubAsset findAsset(String nameRegex) {
        if (assets == null) return null;
        return assets.stream()
                .filter(a -> a.getName() != null && a.getName().matches(nameRegex))
                .findFirst()
                .orElse(null);
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
