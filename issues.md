# Outstanding after Task-6, each with evidence

1. **Connected device tests have not been executed on hardware or emulator.**
   The D-series includes the D13/D14 process-kill restore tests added for the splash fix, and the
   13-item "Check on the phone (1.5.1)" list at the top of `MANUAL_TESTS.md` is entirely unrun.
   Evidence: `:app:compileDebugAndroidTestKotlin` compiles and
   `:app:assembleBenchmark`/`:macrobenchmark:assembleBenchmark` build, but
   `connectedDebugAndroidTest` needs a device and none was attached during this task.