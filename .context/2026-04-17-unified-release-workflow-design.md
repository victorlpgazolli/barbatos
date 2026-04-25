# Unified Release Workflow Design

**Date:** 2026-04-17  
**Status:** Design Review  
**Objective:** Consolidate 3 separate release workflows into 1 unified workflow with granular dispatch controls, eliminating build/compile duplication while enabling flexible debug/maintenance scenarios.

---

## Problem Statement

Currently, 3 separate workflows trigger on tag push:
- `release.yml` (Github Build and Release)
- `apt-release.yml` (Apt Build and Release)
- `snap-release.yml` (Snapcraft Build and Publish)

Each **independently builds and compiles** the same 3 artifacts (linux-x64, linux-arm64, macos-arm64), resulting in:
- 3x the CI/CD runtime (18+ minutes total when all could run in ~6 minutes)
- Code duplication across setup steps (JDK, Node, Python, QEMU, dependencies)
- Maintenance burden: changes to build logic must be made 3 times
- Limited flexibility for debugging (can't easily test 1 arch + 1 platform combo)

## Solution Overview

Consolidate into **1 unified workflow** with this structure:

```
setup-release (ubuntu-latest)
  ├─ Create GitHub Release
  ├─ Update version.txt + commit
  └─ Output: release_tag, versions
  
     ├─ build-linux-x64 (ubuntu-24.04)          [parallel]
     ├─ build-linux-arm64 (ubuntu-24.04)        [parallel]
     └─ build-macos-arm64 (macos-15)            [parallel]
         └─ Upload artifacts (keyed by arch)
  
         ├─ publish-github                      [parallel]
         ├─ publish-apt                         [parallel]
         └─ publish-snapcraft                   [parallel]
```

### Key Features

**1. Granular Dispatch Control**

Workflow dispatch inputs allow cherry-picking builds and platforms:

```yaml
inputs:
  # Which architectures to build
  build-linux-x64:
    type: boolean
    default: true
  build-linux-arm64:
    type: boolean
    default: true
  build-macos-arm64:
    type: boolean
    default: true
  
  # Which platforms to publish to
  publish-github:
    type: boolean
    default: true
  publish-apt:
    type: boolean
    default: true
  publish-snapcraft:
    type: boolean
    default: true
```

Enables scenarios:
- `build=all, publish=all` → Normal release flow
- `build=linux-x64, publish=apt` → Debug APT for x64
- `build=all, publish=github` → Test all builds without publishing
- `build=linux-arm64, publish=snapcraft` → Debug ARM64 snap
- Any other combination

**2. Composite Actions for Code Reuse**

Four composite actions eliminate duplication:

| Action | Purpose | Inputs |
|--------|---------|--------|
| `setup-build-env` | Install JDK, Node, Python, QEMU, deps | `architecture` (for arch-specific deps) |
| `build-linux` | Compile Linux binaries (Kotlin+Bridge+MCP) | `architecture` (x86_64 \| aarch64) |
| `build-macos` | Compile macOS binaries | - |
| `publish-to-platform` | Upload to GitHub/APT/Snapcraft | `platform` (github \| apt \| snapcraft), `artifacts-dir` |

**3. Conditional Job Execution**

Each build/publish job has:
```yaml
if: |
  (github.event_name == 'workflow_dispatch' && inputs.<flag> == 'true') ||
  (github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v'))
```

On tag push: all jobs run (backward compatible).  
On dispatch: only selected jobs run.

---

## Workflow Structure

### setup-release Job
- Runs on: `ubuntu-latest`
- Permissions: `contents: write`
- Steps:
  1. Checkout repository
  2. Update `version.txt` (if tag push, skip for dispatch)
  3. Commit + push to master
  4. Create GitHub Release (if tag push, skip for dispatch)
  5. Output: `release_tag`, `version`

### build-* Jobs (3 parallel)

**build-linux-x64:**
- Runs on: `ubuntu-24.04`
- Needs: `setup-release`
- Calls: `setup-build-env` (x86_64) → `build-linux` (x86_64)
- Uploads: `artifacts/linux-x64/`

**build-linux-arm64:**
- Runs on: `ubuntu-24.04`
- Needs: `setup-release`
- Calls: `setup-build-env` (aarch64) → `build-linux` (aarch64)
- Uploads: `artifacts/linux-arm64/`

**build-macos-arm64:**
- Runs on: `macos-15`
- Needs: `setup-release`
- Calls: `setup-build-env` (arm64) → `build-macos`
- Uploads: `artifacts/macos-arm64/`

### publish-* Jobs (3 parallel)

**publish-github:**
- Runs on: `ubuntu-latest`
- Needs: all build jobs that ran
- Calls: `publish-to-platform` (github)
- Downloads: all build artifacts
- Uploads to GitHub Release

**publish-apt:**
- Runs on: `ubuntu-latest`
- Needs: `build-linux-x64`, `build-linux-arm64`
- Calls: `publish-to-platform` (apt)
- Steps: sign packages → update repo → push to master

**publish-snapcraft:**
- Runs on: `ubuntu-latest`
- Needs: `build-linux-x64`, `build-linux-arm64`
- Calls: `publish-to-platform` (snapcraft)
- Publishes snaps to store

---

## Composite Action Details

### `setup-build-env/action.yml`
Installs common dependencies based on OS + architecture.

**Inputs:**
- `architecture`: x86_64 | aarch64 | arm64 (for runner type)
- `os`: ubuntu | macos (derived from runner)

**Steps:**
1. `actions/setup-java@v5` (JDK 21 for ubuntu, 17 for macos)
2. `actions/setup-node@v6` (v24)
3. `actions/setup-python@v5` (3.11)
4. `docker/setup-qemu-action@v4` (if architecture == aarch64)
5. OS-specific deps (libcurl, cross-compiler toolchain, etc.)

**Output:**
- Environment variables set (LIBGCC_PATH for ARM64, etc.)

### `build-linux/action.yml`

**Inputs:**
- `architecture`: x86_64 | aarch64

**Steps:**
1. Call `setup-build-env` (pass architecture)
2. `make install_dependencies`
3. `make compile_bridge_agent`
4. `make compile_binary ARCH=<architecture>`
5. If x86_64: `make compile_bridge` + `make compile_mcp`
6. If aarch64: Docker compile (Python bridge + MCP server)
7. `make prepare_release ARCH=<architecture>`
8. Upload to artifact storage

### `build-macos/action.yml`

**Steps:**
1. Call `setup-build-env`
2. `make install_dependencies`
3. `make release` (handles all 3 binaries)
4. Package artifacts
5. Upload to artifact storage

### `publish-to-platform/action.yml`

**Inputs:**
- `platform`: github | apt | snapcraft
- `artifacts-dir`: path to built artifacts
- `version`: release version
- `release-tag`: git tag

**Conditional steps based on platform:**
- **github**: Download all artifacts → `gh release upload`
- **apt**: Sign packages → update repo metadata → commit + push
- **snapcraft**: Publish to snap store

---

## Migration Path

1. Create composite actions in `.github/actions/`
2. Modify `release.yml` to new structure with dispatch inputs
3. Delete `apt-release.yml` and `snap-release.yml` (their logic moves to `release.yml`)
4. Test with workflow dispatch before tagging
5. Tag normally on next release

---

## Benefits

| Aspect | Before | After |
|--------|--------|-------|
| **Runtime** | 18+ min (3 full builds) | ~6 min (1 build, 3 publishes parallel) |
| **Code Duplication** | High (setup repeated 3x) | Minimal (composite actions) |
| **Dispatch Control** | None | Granular (any arch/platform combo) |
| **Maintenance** | Change 3 files | Change 1 workflow + actions |
| **Debugging** | Full workflow only | Cherry-pick arch + platform |

---

## Implementation Notes

- **Artifact Storage:** GitHub Actions' built-in artifact storage (upload-artifact v4) with unique names per arch
- **Conditional Dependencies:** Use `needs: [job1, job2]` with `if: inputs.publish-github == 'true'` to handle missing builds
- **Backwards Compatibility:** Tag push behavior unchanged; dispatch adds flexibility
- **Secrets:** Same GPG/Snapcraft credentials; no changes needed
- **No Third-Party Actions:** Follow project constraint; use only GitHub-owned actions (setup-*, upload-artifact, download-artifact, checkout, etc.)

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Job ordering breaks | Use explicit `needs:` chains, test with dispatch first |
| Artifact download fails if build skipped | Use conditional `if:` statements in publish jobs |
| Secrets not available in dispatch | Dispatch doesn't create release; only tag push does |
| macOS build times | Parallel execution compensates; x64+arm64 Linux run concurrently |

