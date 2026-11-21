package com.localbuds.root

/**
 * Where the app's root access stands. Plain data: nothing in this file knows about libsu or Android,
 * so the UI (Phase 3) can show it and tests can build it without a rooted device.
 */
enum class RootState {
    /** Nothing has asked for root in this process yet. */
    UNKNOWN,

    /** A shell is being opened. The root manager's grant prompt may be on screen. */
    CHECKING,

    /** A root shell is open: [RootShell.exec] runs commands as uid 0. */
    GRANTED,

    /**
     * The shell that opened is not root: the user refused the request, or the phone has no root
     * manager. libsu cannot tell the two apart, so neither can we.
     */
    UNAVAILABLE,
}

/**
 * What a command printed and how it ended.
 *
 * Our own type on purpose: libsu's `Shell.Result` stays inside [RootShell], so the rest of the app
 * never imports libsu and a library change touches one file.
 */
data class CommandResult(
    val isSuccess: Boolean,
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>,
) {
    companion object {
        /** Returned when there was no root shell to run on. Nothing was executed. */
        val NOT_RUN = CommandResult(isSuccess = false, exitCode = -1, stdout = emptyList(), stderr = listOf("no root shell"))
    }
}
