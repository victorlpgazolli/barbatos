# APT Repository: Migrate to Custom Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve APT metadata at `https://barbatos.victorlpgazolli.dev/` and host `.deb` binaries on GitHub Releases instead of committing them to git.

**Architecture:** `update-apt-repo.sh` accepts a `PACKAGES_BASE_URL` parameter and rewrites the `Filename:` field in generated `Packages` files to point to GitHub Releases download URLs. The `publish-repo` CI job uploads `.deb` files to the GitHub Release, generates only `dists/` metadata, copies it into `web/`, and pushes to master.

**Tech Stack:** Bash, `apt-ftparchive`, `gpg`, GitHub Actions (`gh` CLI), GitHub Releases

---

### Task 1: Harden .gitignore against accidental binary commits

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Add safety entries to .gitignore**

Open `.gitignore` and append at the end:

```
*.deb
web/pool/
```

- [ ] **Step 2: Verify no existing .deb or web/pool/ files would be untracked**

```bash
git -C /Users/victorgazolli/projects/opensource/barbatos/.worktrees/apt-publish \
  ls-files --others --exclude-standard | grep -E '\.deb$|web/pool/'
```

Expected: no output (nothing would be accidentally staged).

- [ ] **Step 3: Stage the change**

```bash
git -C /Users/victorgazolli/projects/opensource/barbatos/.worktrees/apt-publish \
  add .gitignore
```

---

### Task 2: Update update-apt-repo.sh to rewrite Filename: field

**Files:**
- Modify: `scripts/update-apt-repo.sh`

The key changes:
1. Accept `PACKAGES_BASE_URL` as `$5`
2. Copy `.deb` files into a temporary pool directory (not `REPO_DIR`) so `apt-ftparchive` can hash them without polluting the output
3. After generating each `Packages` file, rewrite `Filename: pool/main/<x>` to `Filename: PACKAGES_BASE_URL/<x>`
4. Remove the `mkdir -p "$REPO_DIR/pool/main"` and `cp ... pool/main/` lines

- [ ] **Step 1: Replace the full content of scripts/update-apt-repo.sh**

```bash
#!/bin/bash
set -e

DEB_PACKAGES_DIR=$1
REPO_DIR=$2
GPG_KEY_ID=$3
GPG_PASSPHRASE=$4
PACKAGES_BASE_URL=$5

if [ -z "$DEB_PACKAGES_DIR" ] || [ -z "$REPO_DIR" ] || [ -z "$GPG_KEY_ID" ] || [ -z "$PACKAGES_BASE_URL" ]; then
    echo "Usage: $0 <deb-packages-dir> <repo-dir> <gpg-key-id> [gpg-passphrase] <packages-base-url>"
    exit 1
fi

echo "Updating APT repository in $REPO_DIR using packages from $DEB_PACKAGES_DIR"
echo "Package download base URL: $PACKAGES_BASE_URL"

mkdir -p "$REPO_DIR/dists/stable/main/binary-amd64"
mkdir -p "$REPO_DIR/dists/stable/main/binary-arm64"

POOL_TMP=$(mktemp -d)
mkdir -p "$POOL_TMP/pool/main"
cp "$DEB_PACKAGES_DIR"/*.deb "$POOL_TMP/pool/main/"

REPO_DIR_ABS=$(realpath "$REPO_DIR")

cd "$POOL_TMP"

for arch in amd64 arm64; do
    echo "Generating Packages for $arch..."
    apt-ftparchive --arch $arch packages pool/main > "$REPO_DIR_ABS/dists/stable/main/binary-$arch/Packages"
    sed -i "s|Filename: pool/main/|Filename: $PACKAGES_BASE_URL/|g" \
        "$REPO_DIR_ABS/dists/stable/main/binary-$arch/Packages"
    gzip -c "$REPO_DIR_ABS/dists/stable/main/binary-$arch/Packages" \
        > "$REPO_DIR_ABS/dists/stable/main/binary-$arch/Packages.gz"
done

cd "$REPO_DIR_ABS"
rm -rf "$POOL_TMP"

for arch in amd64 arm64; do
    apt-ftparchive release "dists/stable/main/binary-$arch" > "dists/stable/main/binary-$arch/Release"
done

cat > dists/stable/apt-release.conf <<EOF
APT::FTPArchive::Release::Origin "Barbatos Repository";
APT::FTPArchive::Release::Label "Barbatos";
APT::FTPArchive::Release::Suite "stable";
APT::FTPArchive::Release::Codename "stable";
APT::FTPArchive::Release::Architectures "amd64 arm64";
APT::FTPArchive::Release::Components "main";
APT::FTPArchive::Release::Description "Barbatos TUI Debugger APT Repository";
EOF

apt-ftparchive -c dists/stable/apt-release.conf release dists/stable > dists/stable/Release
rm dists/stable/apt-release.conf

echo "Signing Release file..."
if [ -n "$GPG_PASSPHRASE" ]; then
    echo "$GPG_PASSPHRASE" | gpg --batch --yes --passphrase-fd 0 --default-key "$GPG_KEY_ID" --clearsign -o dists/stable/InRelease dists/stable/Release
    echo "$GPG_PASSPHRASE" | gpg --batch --yes --passphrase-fd 0 --default-key "$GPG_KEY_ID" -abs -o dists/stable/Release.gpg dists/stable/Release
else
    gpg --batch --yes --default-key "$GPG_KEY_ID" --clearsign -o dists/stable/InRelease dists/stable/Release
    gpg --batch --yes --default-key "$GPG_KEY_ID" -abs -o dists/stable/Release.gpg dists/stable/Release
fi

echo "APT repository update complete."
```

- [ ] **Step 2: Verify the sed rewriting logic**

```bash
echo "Filename: pool/main/barbatos_1.0_amd64.deb" | \
  sed "s|Filename: pool/main/|Filename: https://github.com/victorlpgazolli/barbatos/releases/download/v1.0.0/|g"
```

Expected:
```
Filename: https://github.com/victorlpgazolli/barbatos/releases/download/v1.0.0/barbatos_1.0_amd64.deb
```

- [ ] **Step 3: Stage the change**

```bash
git -C /Users/victorgazolli/projects/opensource/barbatos/.worktrees/apt-publish \
  add scripts/update-apt-repo.sh
```

---

### Task 3: Update the publish-repo job in apt-release.yml

**Files:**
- Modify: `.github/workflows/apt-release.yml` (lines 130-171, `publish-repo` job only)

Changes:
- Remove the `Checkout gh-pages` step
- Add `permissions: contents: write` to the job
- Add a step to upload `.deb` files to the GitHub Release
- Update the `Update APT Repository` step to pass `PACKAGES_BASE_URL` and output to `apt_meta/`
- Replace `Deploy to GitHub Pages` with `Deploy to web/` that copies `dists/` + `barbatos-repo.gpg` into `web/` and pushes to master

**Race condition note:** Both `release.yml` and `apt-release.yml` are triggered by `v*` tags and run in parallel. `release.yml` creates the GitHub Release; `apt-release.yml` needs to upload to it. The upload step uses a retry loop to wait for the release to exist before uploading.

- [ ] **Step 1: Replace the publish-repo job**

Replace lines 130-171 in `.github/workflows/apt-release.yml` (the entire `publish-repo:` block) with:

```yaml
  publish-repo:
    needs: build-and-package
    runs-on: ubuntu-24.04
    if: startsWith(github.ref, 'refs/tags/v')
    permissions:
      contents: write
    steps:
      - name: Checkout repository
        uses: actions/checkout@v6
        with:
          path: main

      - name: Download all artifacts
        uses: actions/download-artifact@v4
        with:
          path: deb-packages
          pattern: deb-packages-*
          merge-multiple: true

      - name: Upload .deb packages to GitHub Release
        run: |
          for i in $(seq 1 20); do
            if gh release upload ${{ github.ref_name }} deb-packages/*.deb --clobber 2>/dev/null; then
              echo "Upload succeeded."
              break
            fi
            echo "Release not ready yet, retrying in 30s... ($i/20)"
            sleep 30
          done
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GH_REPO: ${{ github.repository }}

      - name: Import GPG key
        uses: crazy-max/ghaction-import-gpg@v6
        with:
          gpg_private_key: ${{ secrets.GPG_PRIVATE_KEY }}
          passphrase: ${{ secrets.GPG_PASSPHRASE }}

      - name: Update APT Repository
        run: |
          sudo apt-get update && sudo apt-get install -y apt-utils
          PACKAGES_BASE_URL="https://github.com/${{ github.repository }}/releases/download/${{ github.ref_name }}"
          mkdir -p apt_meta
          bash main/scripts/update-apt-repo.sh \
            deb-packages \
            apt_meta \
            ${{ secrets.GPG_KEY_ID }} \
            "${{ secrets.GPG_PASSPHRASE }}" \
            "$PACKAGES_BASE_URL"

      - name: Deploy to web/
        run: |
          cp -r apt_meta/dists main/web/
          cp main/packaging/barbatos-repo.gpg main/web/barbatos-repo.gpg
          cd main
          git config --global user.name "github-actions[bot]"
          git config --global user.email "github-actions[bot]@users.noreply.github.com"
          git add web/
          git diff --cached --quiet || git commit -m "chore: update apt repository for release ${{ github.ref_name }}"
          git push origin HEAD:master
```

- [ ] **Step 2: Verify the yaml is well-formed**

```bash
python3 -c "import yaml, sys; yaml.safe_load(open('.github/workflows/apt-release.yml'))" \
  && echo "YAML valid" || echo "YAML invalid"
```

Expected: `YAML valid`

- [ ] **Step 3: Stage the change**

```bash
git -C /Users/victorgazolli/projects/opensource/barbatos/.worktrees/apt-publish \
  add .github/workflows/apt-release.yml
```

---

### Task 4: Verify the full staged diff

- [ ] **Step 1: Review all staged changes**

```bash
git -C /Users/victorgazolli/projects/opensource/barbatos/.worktrees/apt-publish \
  diff --cached
```

Expected: only changes in `.gitignore`, `scripts/update-apt-repo.sh`, and `.github/workflows/apt-release.yml`. No `.deb` files, no `web/pool/`, no other files.

- [ ] **Step 2: Confirm Filename: rewrite is present in the script**

```bash
grep "sed.*Filename" \
  /Users/victorgazolli/projects/opensource/barbatos/.worktrees/apt-publish/scripts/update-apt-repo.sh
```

Expected:
```
    sed -i "s|Filename: pool/main/|Filename: $PACKAGES_BASE_URL/|g" \
```

- [ ] **Step 3: Confirm pool/ creation is gone from the script**

```bash
grep "pool/main" \
  /Users/victorgazolli/projects/opensource/barbatos/.worktrees/apt-publish/scripts/update-apt-repo.sh
```

Expected: only `POOL_TMP/pool/main` references (temp dir), no `REPO_DIR/pool/main`.

- [ ] **Step 4: Confirm gh-pages reference is gone from the workflow**

```bash
grep "gh-pages" \
  /Users/victorgazolli/projects/opensource/barbatos/.worktrees/apt-publish/.github/workflows/apt-release.yml
```

Expected: no output.
