import de.vandermeer.asciitable.AsciiTable
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import net.bjoernpetersen.m3u.M3uParser
import net.bjoernpetersen.m3u.model.M3uEntry
import net.bjoernpetersen.m3u.model.MediaPath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

private const val MUSIC_PREFIX = "/Users/chris/Music/Music/Media.localized/Music"
private const val APPLE_MUSIC_PREFIX = "/Users/chris/Music/Music/Media.localized/Apple Music"
private const val MAX_LINE_WIDTH = 150

data class PathMapping(
    val source: String,
    val target: String,
)

val mapping: List<PathMapping> by lazy {
    val stream = {}.javaClass.getResourceAsStream("/mapping.txt") ?: error("mapping.txt not found")
    stream.bufferedReader().useLines { lines ->
        lines
            .filter { it.isNotBlank() && !it.trim().startsWith("#") }
            .map { line ->
                val (source, target) = line.split("=", limit = 2)
                PathMapping(source.trim(), target.trim())
            }.toList()
    }
}

private fun Path.entries() = M3uParser.parse(this)

private fun List<M3uEntry>.report() {
    val table = AsciiTable()

    table.addRule()
    table.addRow("Title", "Path")
    table.addRule()

    this.forEach { entry ->
        table.addRow(
            entry.title,
            entry.location
                .toString()
                .removePrefix(MUSIC_PREFIX)
                .removePrefix(APPLE_MUSIC_PREFIX),
        )
        table.addRule()
    }

    print(table.render(MAX_LINE_WIDTH))
}

@OptIn(ExperimentalPathApi::class)
private fun validatePaths(
    inputPath: Path,
    outputPath: Path,
    overwrite: Boolean?,
) {
    if (!inputPath.exists()) {
        logger.error { "Input file does not exist: $inputPath" }
        exitProcess(1)
    }

    if (outputPath.exists()) {
        if (overwrite == true) {
            outputPath.deleteRecursively()
        } else {
            logger.error { "Output directory already exists: $outputPath" }
            exitProcess(1)
        }
    }
}

private fun M3uEntry.create(pathRoot: Path) {
    logger.info { "Handling ${this.title}... " }

    val sourcePath = (this.location as MediaPath).path
    val sourceStr = sourcePath.toString()

    val relativePath =
        when {
            sourceStr.startsWith(MUSIC_PREFIX) -> {
                sourceStr.removePrefix(MUSIC_PREFIX).removePrefix("/")
            }

            sourceStr.startsWith(APPLE_MUSIC_PREFIX) -> {
                sourceStr
                    .removePrefix(APPLE_MUSIC_PREFIX)
                    .removePrefix("/")
            }

            else -> {
                sourceStr
            }
        }

    val mappedPath =
        mapping
            .firstOrNull { relativePath.startsWith(it.source) }
            ?.let {
                it.target +
                    relativePath
                        .removePrefix(it.source)
            }?.also {
                logger.debug { "Mapping ${this.title} to $it" }
            }
            ?: relativePath

    val targetPath = pathRoot.resolve(mappedPath)
    val targetDir = targetPath.parent

    if (targetDir != null && !targetDir.exists()) {
        targetDir.createDirectories()
    }

    Files.copy(sourcePath, targetPath)
}

private fun List<M3uEntry>.clean(): List<M3uEntry> {
    fun String.baseName(): String = replace(Regex(""" 1(\.[^/\\]+)$"""), "$1")

    val filtered = this.filterNot { it.location.toString().endsWith(".m4p") }

    val grouped = filtered.groupBy { it.location.toString().baseName() }

    return grouped.values.flatMap { group ->
        if (group.size == 1) {
            group
        } else {
            logger.debug { "Cleaning duplicates for ${group.first().title}... " }
            val originals = group.filterNot { it.location.toString().contains(" 1.") }
            originals.ifEmpty { group }
        }
    }
}

fun main(args: Array<String>) {
    val parser = ArgParser("car_playlist")

    val input by parser
        .option(ArgType.String, shortName = "i", description = "Input file (m3u8)")
        .required()
    val output by parser
        .option(ArgType.String, shortName = "o", description = "Output directory")
        .required()
    val overwrite by parser.option(
        ArgType.Boolean,
        shortName = "d",
        description = "Delete output directory if it exists",
    )

    parser.parse(args)

    val inputPath = Paths.get(input)
    val outputPath = Paths.get(output)

    validatePaths(inputPath, outputPath, overwrite)

    val entries = inputPath.entries().clean()
    entries.report()

    outputPath.createDirectory()

    entries.forEach { it.create(outputPath) }
}
