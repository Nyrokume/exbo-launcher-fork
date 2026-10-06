package dev.launcherfork.ui.state

/** Rows of the game-actions menu next to ИГРАТЬ (main/game_actions.fxml), top to bottom. */
enum class GameAction(
    /** i18n key of the row text. */
    val textKey: String,
    /** CSS class of the row icon (layout.css, .ga-icon.<class>). */
    val iconClass: String,
    /** Red row; asks for confirmation before anything happens. */
    val destructive: Boolean = false,
) {
    VERIFY("main.actions.verify", "ga-icon-verify"),
    OPEN_FOLDER("main.actions.open_folder", "ga-icon-folder"),
    SETTINGS("main.actions.settings", "ga-icon-settings"),
    REINSTALL("main.actions.reinstall", "ga-icon-reinstall", destructive = true),
    DELETE("main.actions.delete", "ga-icon-delete", destructive = true),
}

/** Which rows make sense in which client state (disabled rows stay visible, greyed out). */
object GameActions {
    fun isEnabled(action: GameAction, state: ClientState): Boolean = when (action) {
        // a running game is closed first (after a confirmation)
        GameAction.VERIFY -> state == ClientState.Ready || state == ClientState.Running
        // the folder exists as soon as a download started
        GameAction.OPEN_FOLDER -> state != ClientState.NotInstalled
        GameAction.SETTINGS -> true
        GameAction.REINSTALL -> state == ClientState.Ready
        // a paused partial download can be removed too
        GameAction.DELETE -> state == ClientState.Ready || state is ClientState.Paused
    }

    /** Verification of a running game closes it: ask first. Reinstall / delete always ask. */
    fun needsConfirmation(action: GameAction, state: ClientState): Boolean =
        action.destructive || (action == GameAction.VERIFY && state == ClientState.Running)
}
