# APT Repository Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable users to install `barbatos`, `barbatos-bridge`, and `barbatos-mcp` via `apt` and `apt-get` on Debian-based systems (Ubuntu, Debian, WSL).

**Architecture:** Use GitHub Actions to build `.deb` packages for each component, sign them with GPG, and host a static APT repository on GitHub Pages using `reprepro` or a custom script.

**Tech Stack:** Debian Packaging (`dpkg-deb`), GPG, GitHub Actions, GitHub Pages.

---

### Task 1: Debian Package Templates

**Files:**
- Create: `packaging/debian/barbatos/DEBIAN/control`
- Create: `packaging/debian/barbatos-bridge/DEBIAN/control`
- Create: `packaging/debian/barbatos-mcp/DEBIAN/control`

- [x] **Step 1: Create directory structure for barbatos core**
```bash
mkdir -p packaging/debian/barbatos/DEBIAN
mkdir -p packaging/debian/barbatos/usr/bin
```

- [x] **Step 2: Define control file for barbatos core**
```text
Package: barbatos
Version: 1.0.0
Section: utils
Priority: optional
Architecture: arm64
Maintainer: Victor Gazolli <victor@example.com>
Description: Barbatos TUI Debugger for Android
```

- [x] **Step 3: Repeat for bridge and mcp with appropriate dependencies**
(Bridge will need `python3`, `nodejs`, `npm`)
(MCP will need `python3`)

- [x] **Step 4: Commit templates**
```bash
git add packaging/
git commit -m "chore: add debian packaging templates"
```

---

### Task 2: GPG Key Setup for Repository Signing

- [x] **Step 1: Generate a dedicated GPG key for the repository**
(This should be done locally or in a secure environment)

- [x] **Step 2: Export Public Key for users**
```bash
gpg --armor --export <KEY_ID> > packaging/barbatos-repo.gpg
```

- [x] **Step 3: Export Private Key for GitHub Secrets**
(User must add `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` to GitHub Repo Secrets)

- [x] **Step 4: Commit public key and instructions**
```bash
git add packaging/barbatos-repo.gpg
git commit -m "docs: add public GPG key for APT repository"
```

---

### Task 3: GitHub Action: Build and Package

**Files:**
- Create: `.github/workflows/apt-release.yml`

- [ ] **Step 1: Define Build Workflow**
Create a workflow that triggers on tags/releases.
1. Build `barbatos` (Kotlin Native).
2. Bundle `bridge` (Python/JS).
3. Bundle `mcp` (Python).

- [ ] **Step 2: Assemble DEB packages**
```bash
# Example for core
cp build/bin/macosArm64/debugExecutable/barbatos.kexe packaging/debian/barbatos/usr/bin/barbatos
dpkg-deb --build packaging/debian/barbatos
```

- [ ] **Step 3: Sign the packages**
Use `dpkg-sig` or `debsigs` in the workflow.

- [ ] **Step 4: Commit workflow**
```bash
git add .github/workflows/apt-release.yml
git commit -m "ci: add github action for building debian packages"
```

---

### Task 4: GitHub Action: Publish to APT Repo (GitHub Pages)

- [ ] **Step 1: Create `gh-pages` branch**
```bash
git checkout --orphan gh-pages
git rm -rf .
touch index.html
git add index.html
git commit -m "init gh-pages"
git push origin gh-pages
```

- [ ] **Step 2: Implement Repo Update Script**
Use `apt-ftparchive` in the GitHub Action to update `Packages.gz` and `Release` files in the `gh-pages` branch.

- [ ] **Step 3: Automate Release Deployment**
Add a step to `apt-release.yml` that pushes the generated `.deb` and metadata to `gh-pages`.

---

### Task 5: User Installation Documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add Installation Instructions**
```bash
# Add the GPG key
curl -s https://<user>.github.io/barbatos/barbatos-repo.gpg | sudo apt-key add -

# Add the repository
echo "deb [arch=arm64] https://<user>.github.io/barbatos/ stable main" | sudo tee /etc/apt/sources.list.d/barbatos.list

# Install
sudo apt update
sudo apt install barbatos barbatos-bridge barbatos-mcp
```

- [ ] **Step 2: Commit documentation**
```bash
git add README.md
git commit -m "docs: add apt installation instructions"
```
