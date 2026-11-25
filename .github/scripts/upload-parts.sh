#!/usr/bin/env bash
# Uploads every file in a folder, except a jpack manifest (which the workflow uploads last), to the release $TAG. The
# `client` job runs it for the two jars both platforms share, then each platform job for its jpack release folder.
#
# That folder holds the two jars again: they are the very same files, already on the release. A file that is already there
# is left alone instead of being deleted and uploaded again, which would leave a moment in which the other platform's
# manifest names a missing file. Part names carry no hash, so "already there" is checked against the SHA-256 GitHub
# reports for the asset: a file with the same name and different bytes fails the job instead of leaving the two manifests
# disagreeing.
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
      # The file may be on the release all the same: compare it below.
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
