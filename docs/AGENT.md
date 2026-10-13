# Working agent instructions

Read this first, every session. The current task is named in the first paragraph.

You're the working agent for Blocks, an Android app (Kotlin, Compose, Hilt) in this repo. Implement docs/Task-17.md (target 2.7.0) exactly, on a branch task17 from master. The owner's tokens are limited: be fast, decisive and brief.

START
0. Your session doesn't start in the repo. First: cd "C:/Blocks App" (the repo root, with the space and the quotes). Every command below runs from there; if a command ever says "not a git repository", cd there again.
1. One command: git branch --show-current ; git log --oneline master..task17 2>$null ; git status --short
   - No task17 branch yet: git checkout -b task17, then the prep commit and Slice 1. On this first run the new docs/ files show as untracked or modified: that's expected, the prep commit takes them first.
   - Commits named "task17 slice N" are finished slices; continue with the lowest one missing.
   - Modified files mean a previous session stopped mid-slice: run that slice's tests; if green or a quick fix, finish and commit it; otherwise git stash push -m "partial" and redo the slice. Never stash before the "task17 prep" commit exists: the new docs would go with it.
2. Read docs/Task-17.md lines 1-29 once (findings, decisions, rules), then each slice when you start it. Open docs/CODEMAP.md when you need to find a file; docs/NOTES.md only to look up an earlier decision. Don't read SUMMARY.md or the specs.

FINDING THINGS: GREP FIRST
- Search the repo with git grep -n "<name>" (add -- "*.kt" to narrow). It answers in a second; reading files to search, writing Python or PowerShell scripts to search, or searching the web is slower. Never do those for anything inside the repo.
- Use the web only for an Android API you can't find an example of in this repo, and only after git grep came up empty.
- Read a file once, around the lines you need (your Read tool with offset/limit). Don't re-read a file after editing it.

SHELL: WINDOWS POWERSHELL 5.1
- No &&, ||, tail, head, grep, sed, cat <<. Separate commands with ; and trim output with | Select-Object -First N or -Last N. Stay in the repo root (step 0); don't cd anywhere else.
- Never write scratch or output files into the repo (that's how the root filled with a43, c5, t8...). If you must save output, use $env:TEMP.
- Edit with your file tools. If you ever write a file from PowerShell, add -Encoding UTF8. "A?" in console output is just PowerShell showing a non-ASCII character.
- Don't check versions, the environment, or git between steps. The build works; the commit line shows the hash.

GRADLE: ALWAYS THIS FORM (no quotes around the --tests value; several classes: --tests A --tests B; the full suite: no --tests)
   cmd /c "gradlew.bat :app:testDebugUnitTest --tests <class> --offline --console=plain > %TEMP%\g.txt 2>&1" ; "exit=$LASTEXITCODE" ; Select-String -CaseSensitive -Path $env:TEMP\g.txt -Pattern '^e: |FAILED' -Context 0,3 | Select-Object -First 25
- Output goes to a file through cmd, never through a PowerShell pipe: the Gradle daemon can hold a pipe open until the shell times out, and PowerShell wraps Gradle's stderr in NativeCommandError noise.
- exit=0 is the whole answer. Lines starting with w: are warnings: never read them, never fix them.
- No tests in the slice: :app:compileDebugKotlin in place of :app:testDebugUnitTest --tests "...".
- Why did a test fail? Read the result instead of rerunning:
   Select-String -Path app\build\test-results\testDebugUnitTest\*.xml -Pattern '<failure' -Context 1,4 | Select-Object -First 3
- One build per fix. If a build is still running after 10 minutes, it's stuck: ./gradlew --stop, then run it once more.
- Never wait on anything external: no sleep loops, no polling, no waiting for a device. If adb devices doesn't list a phone, skip the phone checks.

READING A RESULT: ONE LOOK, ONE DECISION
- exit=0 means every test passed, including the ones you just added. Passing tests print nothing; that's normal. Don't check that they ran, don't rerun, move to the next step.
- exit is not 0: fix what the error lines say. That's the only other case.
- A shell timeout or warning after BUILD SUCCESSFUL changes nothing: the build is green.
- Once a result is in, don't reopen it ("wait, but..."). Doubting a green build costs more than the rare miss the final gate would catch anyway.

EACH SLICE
1. Read the slice. git grep what you'll change.
2. Make all the slice's edits, code and its named tests, before building. Paste the task's code and adapt names; don't rewrite it.
3. Build and test the slice; fix until exit=0. Then the full suite once: the same command without --tests.
4. git add -A ; git commit -m "task17 slice N: <title>" 2>&1 | Select-Object -First 1
5. One line: "Slice N done: <hash>". Start the next slice at once.

ALWAYS
- Stay in flow: decide and act, at most two sentences of thinking between commands. Don't verify what the compiler or the tests verify; warnings and unused imports are fine.
- Keep going until the final report. Stop early only for a real blocker, and say exactly what it is. Don't ask questions; log choices in docs/NOTES.md in one line each.
- Time-box: after ~15 minutes on one problem, take the simplest option that passes the named tests, note it in docs/NOTES.md, and move on.
- Code marked verified (PlanEditor.kt, AlertPlanner.kt, RecordEditor.kt, StepBook.kt, WalkClock.kt, CalendarLayout.kt, their tests, and StartEarlyWalkTest.kt) is final: don't edit it.
- If an existing test checks something the task changes on purpose, update or delete it and list it in the report. Add only the tests the task names.
- Real names win: if a name in the task doesn't exist, use the real one. Don't refactor beyond the slice.
- If an edit mangles a file: git checkout -- <file>, then redo the edit.
- Phone: only com.roni.blocks.debug. Never install over, clear, force-stop or uninstall com.roni.blocks.
- Finish with Slice 3: final gate, merge task17 into master, tag v2.7.0, push, the short final report, then stop. Don't re-verify finished slices or close old todo lists by re-checking them; mark them done and end.
