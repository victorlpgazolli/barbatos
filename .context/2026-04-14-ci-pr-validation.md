# CI PR Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a GitHub Action that runs unit tests and verifies compilation for all project components (Kotlin Native, Bridge, MCP) to prevent broken PRs.

**Architecture:** A single GitHub Actions workflow with two main jobs: `test` and `build`. The `test` job runs Kotlin/Gradle and Python unit tests. The `build` job ensures all binaries and packages can be successfully compiled/assembled.

**Tech Stack:** GitHub Actions, Gradle (Kotlin Native), Python (pytest).

---

### Task 1: Initialize Branch and Workflow Structure

**Files:**
- Create: `.github/workflows/ci-validation.yml`

- [ ] **Step 1: Create the feature branch from master**
```bash
git checkout master
git pull origin master
git checkout -b feature/pr-test
```

- [ ] **Step 2: Define workflow triggers**
Configure the workflow to run on `push` to any branch and `pull_request` to `master`.

- [ ] **Step 3: Setup Job Environment**
Define the base environment (Ubuntu latest) and necessary setup steps (JDK, Python, Node.js).

---

### Task 2: Implement Unit Testing Job

- [ ] **Step 1: Add Kotlin/Gradle tests step**
```yaml
- name: Run Kotlin Unit Tests
  run: ./gradlew allTests
```

- [ ] **Step 2: Add Python Bridge tests step**
```yaml
- name: Run Bridge Unit Tests
  run: |
    cd bridge
    python3 -m pytest test_bridge.py
```

- [ ] **Step 3: Add Python MCP tests step (if applicable)**
Check for MCP tests and add them to the workflow.

---

### Task 3: Implement Compilation Validation Job

- [ ] **Step 1: Add Kotlin Native compilation step**
```yaml
- name: Compile Native Binary
  run: ./gradlew linkDebugExecutableMacosArm64  # Note: CI might need a Linux target if running on Ubuntu
```

- [ ] **Step 2: Add Bridge and MCP bundling steps**
Verify that `bridge` and `mcp_server` can be packaged or their dependencies installed without errors.

---

### Task 4: Verification and Pull Request

- [ ] **Step 1: Verify workflow syntax**
Use `actionlint` if available or manually check the YAML structure.

- [ ] **Step 2: Push and Create PR**
```bash
git add .github/workflows/ci-validation.yml
git commit -m "ci: add pr validation workflow for tests and builds"
git push origin feature/pr-test
gh pr create --base master --head feature/pr-test --title "ci: pr validation" --body "Adds automated unit tests and build validation for all components."
```

- [ ] **Step 3: Monitor Action**
Check the GitHub Actions tab to ensure the workflow triggers and passes for the new PR.
