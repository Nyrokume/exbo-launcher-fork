package dev.launcherfork.ui

import dev.launcherfork.theme.Theme
import javafx.fxml.FXMLLoader
import javafx.scene.Parent

class Loaded<C>(val root: Parent, val controller: C)

/**
 * FXML loading with i18n resources and a controller factory that injects [UiContext]
 * into controllers having a `(UiContext)` constructor (also for fx:include children).
 */
object Fxml {
    fun <C> load(ctx: UiContext, path: String): Loaded<C> {
        val loader = FXMLLoader(Theme.resource(path), ctx.strings)
        loader.setControllerFactory { type -> instantiate(type, ctx) }
        val root: Parent = loader.load()
        return Loaded(root, loader.getController())
    }

    private fun instantiate(type: Class<*>, ctx: UiContext): Any {
        val withCtx = type.constructors.firstOrNull {
            it.parameterCount == 1 && it.parameterTypes[0] == UiContext::class.java
        }
        return withCtx?.newInstance(ctx) ?: type.getDeclaredConstructor().newInstance()
    }
}
