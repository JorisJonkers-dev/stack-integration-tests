package com.jorisjonkers.personalstack.acceptance

import java.nio.file.Path
import kotlin.system.exitProcess

private const val EXIT_USAGE = 2
private const val EXIT_REFUSED = 3

private const val USAGE =
    "usage: acceptance-runner --config <target-config.yaml> " +
        "[--evaluation-set <path>] [--dry-run] [--results-dir <dir>]"

private data class Cli(
    val configPath: Path,
    val evaluationSetPath: Path?,
    val dryRun: Boolean,
    val resultsDir: Path?,
)

private data class Execution(
    val report: AcceptanceReport,
    val resultsDir: Path,
)

fun main(args: Array<String>) {
    val cli = parseArgs(args)
    if (cli == null) {
        System.err.println(USAGE)
        exitProcess(EXIT_USAGE)
    }
    try {
        val execution = execute(cli)
        val written = writeResults(execution.resultsDir, execution.report)
        println("wrote ${written.jsonPath}")
        println("wrote ${written.markdownPath}")
    } catch (ex: ConfigError) {
        System.err.println("config error: ${ex.message}")
        exitProcess(EXIT_USAGE)
    } catch (ex: EvaluationSetError) {
        System.err.println("evaluation set error: ${ex.message}")
        exitProcess(EXIT_USAGE)
    } catch (ex: ProductionTargetGuardError) {
        System.err.println("refused: ${ex.message}")
        exitProcess(EXIT_REFUSED)
    }
}

private fun execute(cli: Cli): Execution {
    val config = AcceptanceConfigLoader.load(cli.configPath)
    val evaluationSetPath =
        cli.evaluationSetPath
            ?: config.evaluationSetPath?.let(Path::of)
            ?: throw ConfigError("no evaluation set: pass --evaluation-set or set evaluationSetPath in the config")
    val evaluationSet = EvaluationSetLoader.load(evaluationSetPath)
    val effectiveConfig = config.copy(evaluationSetPath = evaluationSetPath.toString())
    val report = AcceptanceRunner.run(effectiveConfig, evaluationSet, cli.dryRun)
    val resultsDir = cli.resultsDir ?: Path.of(effectiveConfig.resultsDir)
    return Execution(report, resultsDir)
}

@Suppress("CyclomaticComplexMethod")
private fun parseArgs(args: Array<String>): Cli? {
    var configPath: Path? = null
    var evaluationSetPath: Path? = null
    var dryRun = false
    var resultsDir: Path? = null
    var index = 0
    while (index < args.size) {
        when (args[index]) {
            "--config" -> {
                configPath = Path.of(valueAfter(args, index))
                index += 2
            }
            "--evaluation-set" -> {
                evaluationSetPath = Path.of(valueAfter(args, index))
                index += 2
            }
            "--dry-run" -> {
                dryRun = true
                index += 1
            }
            "--results-dir" -> {
                resultsDir = Path.of(valueAfter(args, index))
                index += 2
            }
            else -> return null
        }
    }
    return configPath?.let { Cli(it, evaluationSetPath, dryRun, resultsDir) }
}

private fun valueAfter(
    args: Array<String>,
    flagIndex: Int,
): String = args.getOrElse(flagIndex + 1) { throw ConfigError("missing value for ${args[flagIndex]}") }
