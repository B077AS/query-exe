#!/usr/bin/env bash
# Uploads the update files of one platform (everything in the jpack release folder except the manifest, which the
# workflow uploads last) to the release $TAG.
#
# The Windows and the Linux job both upload the client and the launcher jar: they are the very same files (built once by
# the `client` job), so they have the same name. A file that is already on the release is left alone instead of being
# deleted and uploaded again, which would leave a moment in which the other job's manifest names a missing file. Part
# names carry no hash, so "already there" is checked against the SHA-256 GitHub reports for the asset: a file with the
# same name and different bytes fails the job instead of leaving the two manifests disagreeing.
set -euo pipefail

dir="${1:?usage: upload-parts.sh <release folder>}"

# The sha256 GitHub holds for the asset called $1, or nothing when there is no such asset.
remote_sha() {
  gh release view "$TAG" -R "$GITHUB_REPOSITORY" --json assets \
    -q ".assets[] | select(.name == \"$1\") | .digest | sub(\"^sha256:\"; \"\")"
}

find "$dir" -type f ! -name 'jpack-*.json' -print0 | while IFS= read -r -d '' file; do
  name="$(basename "$file")"
  local_sha="$(sha256sum "$file" | cut -d' ' -f1)"
  remote="$(remote_sha "$name")"
  if [ -z "$remote" ]; then
    if ! gh release upload "$TAG" "$file" -R "$GITHUB_REPOSITORY"; then
      # The other platform's job may have uploaded the same file a moment ago.
      remote="$(remote_sha "$name")"
      [ -n "$remote" ] || { echo "upload failed: $name" >&2; exit 1; }
    else
      continue
    fi
  fi
  if [ "$remote" = "$local_sha" ]; then
    echo "already on the release: $name"
  elif [ "${REPLACE_EXISTING:-}" = "true" ]; then
    # A manual rebuild of a release: whatever is on it gets replaced.
    gh release upload "$TAG" "$file" --clobber -R "$GITHUB_REPOSITORY"
  else
    echo "$name is on the release with different content (release $remote, this build $local_sha)" >&2
    exit 1
  fi
done
