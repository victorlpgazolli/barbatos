# APT Repository: Migrate from GitHub Pages to Custom Domain

**Date:** 2026-04-14
**Branch:** feature/apt-publish
**Status:** Approved

## Goal

Serve the APT repository metadata at `https://barbatos.victorlpgazolli.dev/` instead of GitHub Pages. Package binaries (`.deb`) are uploaded to GitHub Releases. Only lightweight metadata is committed to git inside the `web/` directory.

## Architecture

The APT repository is split into two parts with different hosting targets:

| Content | Where | Why |
|---|---|---|
| `dists/` metadata (text files, signatures) | `web/` in git → `barbatos.victorlpgazolli.dev` | Lightweight, needs to be at a stable URL |
| `.deb` packages (binaries) | GitHub Releases | Binary files do not belong in git |

The `Filename:` field inside each `Packages` file will point to the GitHub Releases download URL, connecting the two halves.

## Changes

### `scripts/update-apt-repo.sh`

- Accept a new required parameter `PACKAGES_BASE_URL` (e.g. `https://github.com/victorlpgazolli/barbatos/releases/download/v2.0.0`). In the workflow this is built dynamically from `${{ github.ref_name }}` so it always points to the tag being released.
- Keep `.deb` files in a local temp directory during generation so `apt-ftparchive` can compute correct hashes and sizes
- After generating `Packages`, rewrite every `Filename:` entry from `pool/main/<file>.deb` to `PACKAGES_BASE_URL/<file>.deb` using `sed`
- Do not create a `pool/` directory in `REPO_DIR`
- Output: only `dists/` tree inside `REPO_DIR`

### `.github/workflows/apt-release.yml` — `publish-repo` job

Replace the `gh-pages` checkout + push with:

1. Checkout master branch
2. Download `.deb` artifacts from `build-and-package` jobs
3. Upload `.deb` files to the existing GitHub Release via `gh release upload`
4. Run `update-apt-repo.sh` with a temp directory as `REPO_DIR` and the GitHub Release download URL as `PACKAGES_BASE_URL`
5. Copy generated `dists/` tree into `web/`
6. Copy `packaging/barbatos-repo.gpg` to `web/barbatos-repo.gpg`
7. `git add web/` and push to master

### `.gitignore`

Add two entries:
- `*.deb` — prevent accidental commit of package binaries
- `web/pool/` — prevent accidental commit of pool directory if script is run locally

## Resulting `web/` Structure (committed to git)

```
web/
  barbatos-repo.gpg
  dists/
    stable/
      Release
      InRelease
      Release.gpg
      main/
        binary-amd64/
          Packages
          Packages.gz
          Release
        binary-arm64/
          Packages
          Packages.gz
          Release
```

## User-Facing Setup

```bash
curl -fsSL https://barbatos.victorlpgazolli.dev/barbatos-repo.gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/barbatos.gpg

echo "deb [signed-by=/etc/apt/keyrings/barbatos.gpg] https://barbatos.victorlpgazolli.dev/ stable main" \
  | sudo tee /etc/apt/sources.list.d/barbatos.list

sudo apt update
sudo apt install barbatos
```

Package downloads resolve to:
`https://github.com/victorlpgazolli/barbatos/releases/download/<tag>/barbatos_<version>_amd64.deb`

Each new release regenerates `dists/` pointing to the new tag. The APT repo always serves the latest published version.

## Constraints

- No new worktrees
- No commits — only `git add` at most
- All code and config in English
- No comments added to existing code
- No emoji
