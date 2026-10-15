# TASK 30: Faster builds (no app version)

Only build files change; the app is byte-identical, so **no version bump and no CHANGELOG entry**. Runs **after** v3.5.2 is tagged. One agent, `C:/Blocks App`, branch `task-30/build-speed`; it ends with a merge into `master` and the tag `build-speed-1`.

## Decisions (Roni, 15 Oct)

| Topic | Decision |
|---|---|
| Gradle memory | 4 GB with ParallelGC; the Kotlin daemon 2 GB (Roni's PC has 16 GB) |
| Build cache | On |
| Compose compiler reports | Only on request: `-PcomposeReports` |
| Tests | In parallel forks; `org.gradle.parallel=true` |
| The full suite | Still after every slice (the safety net stays), just faster |

## Rules

1. `git checkout -b task-30/build-speed` ; `git add docs` ; `git rm docs/Task-29.md docs/AGENT-29.md` ; `git commit -m "task-30 prep: docs"`. Then `task-30 slice N: <title>`.
2. **Nothing in the app changes**: no source file under `app/src`, no dependency, no version. All tests stay green, untouched.
3. Times are measured with PowerShell's `Measure-Command`, each command exactly as written, and noted in `docs/NOTES.md`.

---

## Slice 1: Measure, change, measure

**1. Before** (on the prep commit). Run each line once; note the seconds as "before".
```
cmd /c "gradlew.bat --stop" ; cmd /c "gradlew.bat clean --offline > %TEMP%\g.txt 2>&1"
(Measure-Command { cmd /c "gradlew.bat :app:assembleDebug --offline --console=plain > %TEMP%\g.txt 2>&1" }).TotalSeconds
```
- **Clean debug build:** the second line above.
- **Incremental:** add the line `// speed probe` at the end of `feature/home/Home.kt`, run the `:app:assembleDebug` line again, then remove the probe.
- **Full suite:**
  ```
  (Measure-Command { cmd /c "gradlew.bat :app:testDebugUnitTest --rerun --offline --console=plain > %TEMP%\g.txt 2>&1" }).TotalSeconds
  ```

**2. `gradle.properties`** (replace the active lines; keep the comments that still apply):
```properties
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx2g -XX:+UseParallelGC
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.configuration-cache=true
kotlin.code.style=official
```

**3. `app/build.gradle.kts`.**
- Compose reports only when asked:
  ```kotlin
  composeCompiler {
      // Stability reports (architecture.md §16) cost time on every compile: only with -PcomposeReports.
      if (providers.gradleProperty("composeReports").isPresent) {
          reportsDestination = layout.buildDirectory.dir("compose_compiler")
          metricsDestination = layout.buildDirectory.dir("compose_compiler")
      }
      stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
  }
  ```
- In the existing `tasks.withType<Test>().configureEach { }`, add:
  ```kotlin
  // Robolectric classes start slowly; run them side by side. At most 3 forks of 1 GB each, so a 16 GB PC
  // still has room for Android Studio, the Gradle daemon (4 GB) and the Kotlin daemon (2 GB).
  maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 3)
  maxHeapSize = "1g"
  ```

**4. After.** Run the same three measurements exactly as before. The `--stop` + `clean` before the clean build stays: the build cache is what should make it faster now.

**5. Check.**
- `cmd /c "gradlew.bat :app:assembleDebug -PcomposeReports --offline > %TEMP%\g.txt 2>&1"` → `app/build/compose_compiler/` has files.
- A normal `:app:assembleDebug` doesn't write them.

**6. If a test fails** only when running in parallel (it shares something global between test classes):
- note which one in `docs/NOTES.md`;
- set `maxParallelForks = 1` again;
- finish the task. Don't change tests. The planning agent fixes the test's isolation in a later task.

`docs/NOTES.md`, "Build speed (Task-30)": the three before/after times, the cores the forks used, anything from step 6.

---

## Slice 2: Docs and finish

1. `architecture.md` §16: "Compose compiler stability reports: `gradlew :app:assembleDebug -PcomposeReports` → `app/build/compose_compiler/`."
2. Final gate, then:
   ```
   git checkout master ; git merge --no-ff task-30/build-speed -m "merge task-30 build speed" ; git tag build-speed-1 ; git push origin master build-speed-1
   ```
3. A report of at most 6 lines with the three before/after times, then stop.
