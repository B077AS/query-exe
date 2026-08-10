package com.queryexe.update;

import lombok.Data;

/** One entry of a GitHub release's {@code assets} array. */
@Data
public class GitHubAsset {
    private String name;
    private String browser_download_url;
}
