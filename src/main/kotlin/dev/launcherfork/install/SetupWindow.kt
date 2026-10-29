package dev.launcherfork.install

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.components.LogoMark
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.OverrunStyle
import javafx.scene.control.ProgressBar
import javafx.scene.control.TextField
import javafx.scene.image.Image
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread

/** Per-user setup, drawn with the launcher's pre-main window (auth frame, zone theme, login form). */
fun main(args: Array<String>) {
    if (System.getProperty("prism.text") == null) System.setProperty("prism.text", "native")
    if (System.getProperty("prism.lcdtext") == null) System.setProperty("prism.lcdtext", "true")
    Application.launch(SetupApp::class.java, *args)
}

class SetupApp : Application() {
    private val ru = System.getProperty("user.language").equals("ru", ignoreCase = true)
    private lateinit var stage: Stage
    private lateinit var message: Label
    private lateinit var progress: ProgressBar
    private lateinit var detail: Label
    private lateinit var pathField: TextField
    private lateinit var next: Button
    private lateinit var change: Button
    private lateinit var desktop: CheckBox
    private lateinit var startup: CheckBox
    private var dragX = 0.0
    private var dragY = 0.0
    private var running = false
    private var phase = 1

    override fun start(stage: Stage) {
        this.stage = stage
        val args = parameters.raw
        val setup = option(args, "--setup") ?: error("missing --setup")
        val initial = installDir(Path.of(option(args, "--dir") ?: defaultDir()))
        val payload = option(args, "--payload")

        pathField = TextField(initial.toString()).apply {
            styleClass.addAll("zone-input", "zone-radius-sm")
            isEditable = false
        }
        desktop = check(if (ru) "Создать ярлык на рабочем столе" else "Create desktop shortcut", true)
        startup = check(if (ru) "Создать ярлык в автозагрузке" else "Create start-up shortcut", true)
        message = Label().apply {
            isWrapText = true
            maxWidth = Double.MAX_VALUE
        }
        progress = ProgressBar().apply {
            styleClass.add("zone-progress")
            maxWidth = Double.MAX_VALUE
            this.progress = ProgressBar.INDETERMINATE_PROGRESS
            isVisible = false
            isManaged = false
        }
        detail = Label().apply {
            styleClass.add("zone-description")
            maxWidth = Double.MAX_VALUE
            textOverrun = OverrunStyle.CENTER_ELLIPSIS
            isVisible = false
            isManaged = false
        }
        next = Button(if (ru) "Далее" else "Next").apply {
            styleClass.addAll("zone-primary", "zone-radius-sm")
            isMnemonicParsing = false
            maxWidth = Double.MAX_VALUE
            setOnAction { onNext(setup, payload) }
        }
        change = Button(if (ru) "Изменить папку" else "Change folder").apply {
            styleClass.add("zone-link-accent")
            isMnemonicParsing = false
            setOnAction { chooseFolder() }
        }

        val logo = LogoMark().apply {
            logoWidth = 150.0
            styleClass.addAll("zone-logo", "zone-login-logo")
            accessibleText = "EXBO"
        }
        val subtitle = Label(if (ru) "Установка лаунчера" else "Launcher installation").apply {
            styleClass.add("zone-description")
        }
        val top = VBox(logo, subtitle).apply {
            styleClass.addAll("zone-top", "zone-top-plain", "zone-login-top")
            alignment = Pos.CENTER
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        val form = VBox(message, progress, detail, VBox(10.0, desktop, startup), pathField, change, next).apply {
            styleClass.add("zone-bottom")
        }
        val panel = VBox(top, form).apply { styleClass.addAll("zone-panel", "zone-frame") }

        val drag = Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }
        val hide = windowButton("window-hide") { stage.isIconified = true }
        val close = windowButton("window-close", "auth-close") { if (!running) Platform.exit() }
        val title = HBox(drag, hide, close).apply {
            styleClass.add("auth-title-bar")
            alignment = Pos.CENTER_LEFT
        }
        val frame = Region().apply {
            styleClass.addAll("auth-window-frame", "zone-radius-md")
            isMouseTransparent = true
        }
        val root = StackPane(BorderPane().apply {
            this.top = title
            center = panel
        }, frame).apply {
            styleClass.addAll("auth-window", "zone-radius-md")
        }
        root.clip = Rectangle().apply {
            arcWidth = Theme.AUTH_WINDOW_RADIUS * 2
            arcHeight = Theme.AUTH_WINDOW_RADIUS * 2
            widthProperty().bind(root.widthProperty())
            heightProperty().bind(root.heightProperty())
        }
        installDrag(title)
        installDrag(top)

        refreshMessage()
        pathField.textProperty().addListener { _, _, _ -> refreshMessage() }

        stage.initStyle(StageStyle.TRANSPARENT)
        stage.isResizable = false
        stage.title = if (ru) "Установка лаунчера «EXBO»" else "EXBO Launcher installation"
        stage.icons += Theme.appIconResources().mapNotNull { path ->
            Theme::class.java.getResource(path)?.toExternalForm()?.let { Image(it) }
        }
        val scene = Scene(root, Theme.AUTH_WINDOW_WIDTH, 660.0, Color.TRANSPARENT)
        Theme.apply(scene, if (ru) "ru" else "en")
        scene.stylesheets += listOf("layout.css", "zone-auth.css", "zone-steps.css").map { Theme.stylesheet(it) }
        stage.scene = scene
        stage.show()
    }

    private fun onNext(setup: String, payload: String?) {
        val dir = Path.of(pathField.text.trim())
        if (running || foreign(dir)) return
        running = true
        next.isDisable = true
        change.isDisable = true
        desktop.isDisable = true
        startup.isDisable = true
        phase = 1
        message.styleClass.setAll("zone-status")
        message.text = phaseLabel(1, null, null)
        detail.text = ""
        showDetail(false)
        progress.progress = ProgressBar.INDETERMINATE_PROGRESS
        progress.isManaged = true
        progress.isVisible = true
        val progressFile = Files.createTempFile("launcherfork-setup-", ".progress")
        val command = listOf(
            setup,
            "/S",
            "/DESKTOP=${if (desktop.isSelected) 1 else 0}",
            "/STARTUP=${if (startup.isSelected) 1 else 0}",
            "/D=${dir.toAbsolutePath()}",
        )
        thread(name = "setup-copy", isDaemon = true) {
            val code = try {
                val proc = ProcessBuilder(command).apply {
                    if (!payload.isNullOrBlank()) environment()["LAUNCHERFORK_PAYLOAD"] = payload
                    environment()["LAUNCHERFORK_PROGRESS"] = progressFile.toAbsolutePath().toString()
                    redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    redirectError(ProcessBuilder.Redirect.DISCARD)
                }.start()
                var pending = ""
                var offset = 0L
                while (true) {
                    val (nextOffset, chunk) = readProgress(progressFile, offset)
                    offset = nextOffset
                    if (chunk.isNotEmpty()) {
                        pending += chunk
                        val lines = pending.split("\n")
                        pending = lines.last()
                        val complete = lines.dropLast(1).filter { it.isNotBlank() }
                        if (complete.isNotEmpty()) Platform.runLater { complete.forEach(::applyProgress) }
                    }
                    if (!proc.isAlive) break
                    Thread.sleep(80)
                }
                val (_, tail) = readProgress(progressFile, offset)
                if (tail.isNotEmpty()) {
                    val complete = (pending + tail).lineSequence().filter { it.isNotBlank() }.toList()
                    if (complete.isNotEmpty()) Platform.runLater { complete.forEach(::applyProgress) }
                }
                proc.waitFor()
            } catch (_: Exception) {
                -1
            } finally {
                runCatching { Files.deleteIfExists(progressFile) }
            }
            Platform.runLater {
                running = false
                next.isDisable = false
                if (code == 0) {
                    progress.progress = 1.0
                    showDetail(false)
                    message.styleClass.setAll("zone-notice")
                    message.text = if (ru) "Лаунчер «EXBO» установлен." else "EXBO Launcher is installed."
                    next.text = if (ru) "Запустить" else "Launch"
                    next.setOnAction {
                        val exe = dir.resolve("LauncherFork.exe").toFile()
                        if (exe.isFile) ProcessBuilder(exe.absolutePath).start()
                        Platform.exit()
                    }
                } else {
                    progress.isManaged = false
                    progress.isVisible = false
                    showDetail(false)
                    change.isDisable = false
                    desktop.isDisable = false
                    startup.isDisable = false
                    message.styleClass.setAll("zone-error")
                    message.text = when (code) {
                        2 -> if (ru) "В этой папке уже есть другая установка. Выберите другую папку."
                        else "That folder belongs to another installation. Choose a different folder."
                        else -> if (ru) "Установка не выполнена. Повторите загрузку установщика."
                        else "Setup did not finish. Download the installer again."
                    }
                }
            }
        }
    }

    private fun applyProgress(raw: String) {
        val parts = raw.trim().split(' ', limit = 4)
        when (parts.firstOrNull()) {
            "p" -> {
                phase = parts.getOrNull(1)?.toIntOrNull() ?: phase
                message.styleClass.setAll("zone-status")
                message.text = phaseLabel(phase, null, null)
                showDetail(false)
                if (phase >= 4) progress.progress = 1.0
                else if (phase <= 1) progress.progress = ProgressBar.INDETERMINATE_PROGRESS
            }
            "v", "c" -> {
                val index = parts.getOrNull(1)?.toIntOrNull() ?: return
                val total = parts.getOrNull(2)?.toIntOrNull() ?: return
                if (total <= 0) return
                val slot = when (phase) {
                    2 -> 1.0
                    3 -> 2.0
                    else -> 0.0
                }
                progress.progress = ((slot + index.toDouble() / total) / 3.0).coerceIn(0.0, 1.0)
                message.styleClass.setAll("zone-status")
                message.text = phaseLabel(phase, index, total)
                val file = parts.getOrNull(3).orEmpty()
                detail.text = file
                showDetail(file.isNotEmpty())
            }
        }
    }

    private fun phaseLabel(phase: Int, index: Int?, total: Int?): String {
        val name = when (phase) {
            2 -> if (ru) "Копирование" else "Copying"
            3 -> if (ru) "Проверка копии" else "Checking the installed copy"
            4 -> if (ru) "Ярлыки" else "Shortcuts"
            else -> if (ru) "Проверка файлов" else "Checking files"
        }
        return if (index != null && total != null) {
            if (ru) "$name · $index из $total" else "$name · $index of $total"
        } else {
            "$name…"
        }
    }

    private fun showDetail(on: Boolean) {
        detail.isManaged = on
        detail.isVisible = on
    }

    /** New UTF-8 text appended to the installer's progress file since [offset]. */
    private fun readProgress(path: Path, offset: Long): Pair<Long, String> {
        if (!Files.isRegularFile(path)) return offset to ""
        return try {
            RandomAccessFile(path.toFile(), "r").use { raf ->
                val size = raf.length()
                if (size <= offset) return offset to ""
                val bytes = ByteArray((size - offset).toInt())
                raf.seek(offset)
                raf.readFully(bytes)
                size to String(bytes, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            offset to ""
        }
    }

    private fun chooseFolder() {
        if (running) return
        val chooser = DirectoryChooser()
        val current = Path.of(pathField.text.trim())
        val start = when {
            Files.isDirectory(current) -> current.toFile()
            current.parent != null && Files.isDirectory(current.parent) -> current.parent.toFile()
            else -> null
        }
        if (start != null) chooser.initialDirectory = start
        val picked = chooser.showDialog(stage) ?: return
        pathField.text = installDir(picked.toPath()).toString()
    }

    private fun refreshMessage() {
        if (running) return
        val dir = runCatching { Path.of(pathField.text.trim()) }.getOrNull()
        val installed = dir != null && Files.isRegularFile(dir.resolve(".launcherfork-install"))
        val blocked = dir != null && foreign(dir)
        message.styleClass.setAll(if (blocked) "zone-error" else if (installed) "zone-notice" else "zone-description")
        message.text = when {
            blocked -> if (ru) "В этой папке уже есть runtime или app, но это не установка лаунчера «EXBO»."
            else "This folder already has runtime or app, but it is not an EXBO Launcher install."
            installed -> if (ru) "Лаунчер «EXBO» уже установлен в эту папку. Нажмите «Далее», чтобы переустановить."
            else "EXBO Launcher is already installed in this folder. Click Next to reinstall."
            else -> if (ru) "Выберите папку лаунчера и нажмите «Далее». Игра скачивается уже из лаунчера."
            else "Choose the launcher folder and click Next. The game is downloaded from the launcher."
        }
        next.isDisable = blocked
    }

    private fun foreign(dir: Path): Boolean {
        if (Files.isRegularFile(dir.resolve(".launcherfork-install"))) return false
        return Files.isDirectory(dir.resolve("runtime")) || Files.isDirectory(dir.resolve("app"))
    }

    private fun check(text: String, on: Boolean) = CheckBox(text).apply {
        styleClass.addAll("zone-check", "zone-radius-sm")
        isMnemonicParsing = false
        isSelected = on
    }

    private fun windowButton(vararg classes: String, action: () -> Unit) = Button().apply {
        styleClass.addAll(listOf("window-button") + classes)
        isFocusTraversable = false
        isMnemonicParsing = false
        graphic = Region().apply { styleClass.add("window-glyph") }
        setOnAction { action() }
    }

    private fun installDrag(node: javafx.scene.Node) {
        node.setOnMousePressed { e ->
            dragX = e.screenX - stage.x
            dragY = e.screenY - stage.y
        }
        node.setOnMouseDragged { e ->
            stage.x = e.screenX - dragX
            stage.y = e.screenY - dragY
        }
    }

    private fun option(args: List<String>, name: String): String? {
        val i = args.indexOf(name)
        return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
    }

    private fun defaultDir(): String {
        val local = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        return Path.of(local, "LauncherFork").toString()
    }
}

/** The folder the setup exe will use: the chosen path, ending in LauncherFork. */
internal fun installDir(chosen: Path): Path {
    val leaf = chosen.fileName?.toString().orEmpty()
    val parent = chosen.parent
    return if (leaf.equals("LauncherFork", ignoreCase = true) && parent != null) parent.resolve("LauncherFork")
    else chosen.resolve("LauncherFork")
}
