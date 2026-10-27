package dev.launcherfork.data.exbo

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Private copy of the installed `java` tree for the HWID helper.
 * The stub resolves its args file from the exe, so this copy cannot be the installed `java` directory
 * or it would rewrite the real launcher args. The copy used to live at a fixed path in `java.io.tmpdir`
 * and was reused whenever `java.exe` already existed, which let a planted exe or dll survive. It now
 * stays under [exboRoot]`/launcher-fork/jre-layout` and is used only when every file matches the
 * installed tree by SHA-256.
 */
internal fun jreLayout(exboRoot: Path): Path {
    retireLegacyJreTemp()
    if (!Files.isDirectory(exboRoot) || Files.isSymbolicLink(exboRoot)) throw IOException(JRE_INTEGRITY_ERROR)
    val layout = exboRoot.resolve("launcher-fork").resolve("jre-layout")
    val source = exboRoot.resolve("java")
    val dest = layout.resolve("java")
    if (!Files.isDirectory(source) || !confined(exboRoot, source)) throw IOException(JRE_INTEGRITY_ERROR)
    val forkDir = exboRoot.resolve("launcher-fork")
    if (Files.isSymbolicLink(forkDir) || Files.isSymbolicLink(layout)) throw IOException(JRE_INTEGRITY_ERROR)
    if (Files.exists(forkDir) && !confined(exboRoot, forkDir)) throw IOException(JRE_INTEGRITY_ERROR)
    Files.createDirectories(layout)
    if (!confined(exboRoot, layout)) throw IOException(JRE_INTEGRITY_ERROR)
    if (!jreTreeMatches(source, dest)) {
        deleteTree(dest)
        try {
            copyTree(source, dest)
        } catch (e: IOException) {
            deleteTree(dest)
            throw if (e.message == JRE_INTEGRITY_ERROR) e else IOException(JRE_INTEGRITY_ERROR)
        }
        if (!confined(exboRoot, dest) || !jreTreeMatches(source, dest)) {
            deleteTree(dest)
            throw IOException(JRE_INTEGRITY_ERROR)
        }
    }
    return layout
}

/**
 * True when [dest] is a regular-file copy of [source], including `bin/java.exe`.
 * A symlink, junction, extra file, or hash mismatch is not a match.
 */
internal fun jreTreeMatches(source: Path, dest: Path): Boolean {
    if (Files.isSymbolicLink(source) || Files.isSymbolicLink(dest)) return false
    if (!Files.isDirectory(source) || !Files.isDirectory(dest)) return false
    val sourceFiles = HashMap<String, String>()
    val destFiles = HashMap<String, String>()
    if (!collectTree(source, sourceFiles) || !collectTree(dest, destFiles)) return false
    return sourceFiles == destFiles && sourceFiles.containsKey("bin/java.exe")
}

/**
 * Copies [from] onto [to]. Refuses links and any path that would leave [to], so a planted directory
 * cannot redirect a file into another tree.
 */
internal fun copyTree(from: Path, to: Path) {
    val sourceRoot = from.toAbsolutePath().normalize()
    val destRoot = to.toAbsolutePath().normalize()
    if (!Files.isDirectory(sourceRoot) || Files.isSymbolicLink(sourceRoot)) throw IOException(JRE_INTEGRITY_ERROR)
    if (destRoot.startsWith(sourceRoot) || sourceRoot.startsWith(destRoot)) throw IOException(JRE_INTEGRITY_ERROR)
    val entries = ArrayList<Path>()
    Files.walk(sourceRoot).use { paths ->
        paths.forEach { src ->
            if (src == sourceRoot) return@forEach
            if (Files.isSymbolicLink(src)) throw IOException(JRE_INTEGRITY_ERROR)
            if (!Files.isDirectory(src) && !Files.isRegularFile(src, LinkOption.NOFOLLOW_LINKS)) {
                throw IOException(JRE_INTEGRITY_ERROR)
            }
            val relative = sourceRoot.relativize(src.toAbsolutePath().normalize())
            if (!safeLayoutRelative(relative)) throw IOException(JRE_INTEGRITY_ERROR)
            entries.add(src)
        }
    }
    if (Files.isSymbolicLink(destRoot)) throw IOException(JRE_INTEGRITY_ERROR)
    Files.createDirectories(destRoot)
    val destReal = destRoot.toRealPath()
    for (src in entries) {
        val dest = destRoot.resolve(sourceRoot.relativize(src)).normalize()
        if (!dest.startsWith(destRoot)) throw IOException(JRE_INTEGRITY_ERROR)
        if (Files.isSymbolicLink(dest)) throw IOException(JRE_INTEGRITY_ERROR)
        val parent = dest.parent
        if (parent != null && Files.exists(parent) && realEscapes(destReal, parent)) {
            throw IOException(JRE_INTEGRITY_ERROR)
        }
        if (Files.isDirectory(src)) {
            Files.createDirectories(dest)
        } else {
            Files.createDirectories(dest.parent)
            Files.copy(
                src,
                dest,
                StandardCopyOption.REPLACE_EXISTING,
                LinkOption.NOFOLLOW_LINKS,
            )
        }
        if (realEscapes(destReal, dest)) throw IOException(JRE_INTEGRITY_ERROR)
    }
}

/**
 * A directory created under the private layout. A pre-planted link is refused so the HWID helper
 * cannot write `args_new.txt` or class files through it.
 */
internal fun realWorkDirectory(parent: Path, name: String): Path {
    if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
        throw IOException(JRE_INTEGRITY_ERROR)
    }
    val child = parent.resolve(name)
    if (Files.isSymbolicLink(child)) throw IOException(JRE_INTEGRITY_ERROR)
    Files.createDirectories(child)
    if (Files.isSymbolicLink(child)) throw IOException(JRE_INTEGRITY_ERROR)
    if (realEscapes(parent.toRealPath(), child)) throw IOException(JRE_INTEGRITY_ERROR)
    return child
}

/** Deletes [root] without following a link or junction out of the tree. */
internal fun deleteTree(root: Path) {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
    if (Files.isSymbolicLink(root)) {
        Files.deleteIfExists(root)
        return
    }
    val lexicalRoot = root.toAbsolutePath().normalize()
    val rootReal = root.toRealPath()
    val remove = ArrayList<Path>()
    Files.walkFileTree(
        root,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val lexical = dir.toAbsolutePath().normalize()
                if (!lexical.startsWith(lexicalRoot)) return FileVisitResult.SKIP_SUBTREE
                if (dir != root && (Files.isSymbolicLink(dir) || realEscapes(rootReal, dir))) {
                    remove.add(dir)
                    return FileVisitResult.SKIP_SUBTREE
                }
                remove.add(dir)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val lexical = file.toAbsolutePath().normalize()
                if (lexical.startsWith(lexicalRoot)) remove.add(file)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                val lexical = file.toAbsolutePath().normalize()
                if (lexical.startsWith(lexicalRoot)) remove.add(file)
                return FileVisitResult.CONTINUE
            }
        },
    )
    for (path in remove.asReversed()) {
        val lexical = path.toAbsolutePath().normalize()
        if (lexical.startsWith(lexicalRoot)) Files.deleteIfExists(path)
    }
}

private fun collectTree(root: Path, out: MutableMap<String, String>): Boolean {
    val rootAbs = root.toAbsolutePath().normalize()
    val rootReal = try {
        rootAbs.toRealPath()
    } catch (_: IOException) {
        return false
    }
    return try {
        Files.walk(rootAbs).use { paths ->
            for (path in paths) {
                if (Files.isSymbolicLink(path)) return false
                val lexical = path.toAbsolutePath().normalize()
                if (!lexical.startsWith(rootAbs) || realEscapes(rootReal, path)) return false
                if (path == rootAbs) continue
                val relative = rootAbs.relativize(lexical).toString().replace('\\', '/')
                if (!safeLayoutName(relative)) return false
                if (Files.isDirectory(path)) {
                    out["$relative/"] = ""
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    out[relative] = exboSha256Hex(path)
                } else {
                    return false
                }
            }
        }
        true
    } catch (_: IOException) {
        false
    }
}

private fun confined(parent: Path, child: Path): Boolean {
    if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(child)) return false
    return try {
        !realEscapes(parent.toRealPath(), child)
    } catch (_: IOException) {
        false
    }
}

private fun realEscapes(rootReal: Path, path: Path): Boolean =
    try {
        !path.toRealPath().startsWith(rootReal)
    } catch (_: IOException) {
        true
    }

private fun safeLayoutRelative(relative: Path): Boolean {
    if (relative.toString().isEmpty()) return false
    return relative.none { part ->
        val name = part.toString()
        name.isEmpty() || name == "." || name == ".."
    }
}

private fun safeLayoutName(relative: String): Boolean {
    if (relative.isEmpty() || relative.startsWith("/") || relative.contains(':')) return false
    return relative.split('/').none { it.isEmpty() || it == "." || it == ".." }
}

private fun retireLegacyJreTemp() {
    val legacy = Path.of(System.getProperty("java.io.tmpdir"), "exbo-fork-jre")
    try {
        deleteTree(legacy)
    } catch (_: IOException) {
        // The helper never reads this path. A failed cleanup leaves the old files unused.
    }
}
