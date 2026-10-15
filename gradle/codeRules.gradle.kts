// Code rules (3.5.0): fails the build when app code breaks one of the rules below. Runs before every
// build (preBuild), so the Play button catches it too. Each rule names the fix. No escape hatch on purpose.
val codeRules = tasks.register("codeRules") {
    group = "verification"
    description = "Fails on !!, GlobalScope, runBlocking, and experimental opt-ins outside designsystem/."
    val root = layout.projectDirectory.dir("src/main/java").asFile
    val sources = layout.projectDirectory.dir("src/main/java").asFileTree.matching { include("**/*.kt") }
    inputs.files(sources)
    val stamp = layout.buildDirectory.file("codeRules/ok")
    outputs.file(stamp)
    doLast {
        val strings = Regex("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'")
        val rules = listOf(
            Triple(Regex("!!"), false, "no !! - use ?:, ?.let, an early return, or a helper like Segment.actualMinutes (architecture.md §14)"),
            Triple(Regex("\\bGlobalScope\\b"), false, "no GlobalScope - inject @ApplicationScope"),
            Triple(Regex("\\brunBlocking\\b"), false, "no runBlocking in app code - make the caller suspend"),
            // 3.5.1 turns this on
            // Triple(Regex("@OptIn\\(|\\bExperimental\\w*Api\\b"), true, "experimental APIs only inside designsystem/ - wrap the component there"),
        )
        val problems = mutableListOf<String>()
        sources.files.sortedBy { it.path }.forEach { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            val inDesignSystem = path.contains("/designsystem/")
            var inBlockComment = false
            file.readLines().forEachIndexed { index, raw ->
                var line = raw
                if (inBlockComment) {
                    val end = line.indexOf("*/")
                    if (end < 0) return@forEachIndexed
                    line = line.substring(end + 2); inBlockComment = false
                }
                line = line.replace(strings, "\"\"")
                val open = line.indexOf("/*")
                if (open >= 0) {
                    val close = line.indexOf("*/", open + 2)
                    if (close < 0) { inBlockComment = true; line = line.substring(0, open) }
                    else line = line.substring(0, open) + line.substring(close + 2)
                }
                line = line.substringBefore("//")
                rules.forEach { (regex, designSystemOk, fix) ->
                    if ((!designSystemOk || !inDesignSystem) && regex.containsMatchIn(line)) {
                        problems += "$path:${index + 1}: $fix\n    ${raw.trim()}"
                    }
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Code rules broken (${problems.size}):\n" + problems.joinToString("\n"))
        }
        stamp.get().asFile.apply { parentFile.mkdirs(); writeText("ok") }
    }
}
