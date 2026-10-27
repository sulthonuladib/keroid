package com.sulthonuladib.keroid.term

/** Preferred geometry is independent of the attached mirror's confirmed grid. */
internal data class TerminalGeometry(val cols: Int, val rows: Int, val cellWidth: Int, val cellHeight: Int)

/** Main-thread state, retained across attachments. Sends resize before claim. */
internal class TerminalSizing {
    sealed interface Command {
        data class Resize(val geometry: TerminalGeometry) : Command
        data class Claim(val activation: Long) : Command
    }

    var desired: TerminalGeometry? = null
        private set
    @Volatile private var active = false
    @Volatile private var activation = 0L
    private var claimed = false
    private var sent: TerminalGeometry? = null
    private var send: ((Command) -> Unit)? = null

    fun request(geometry: TerminalGeometry) {
        if (geometry.cols <= 0 || geometry.rows <= 0) return
        desired = geometry
        flush()
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        activation++
        claimed = false
        flush()
    }

    fun attach(send: (Command) -> Unit) {
        this.send = send
        sent = null
        claimed = false
        flush()
    }

    fun detach() {
        send = null
        sent = null
        claimed = false
    }

    /** Re-check on the IO writer: a queued claim must not survive pause/resume. */
    fun mayClaim(command: Command.Claim): Boolean = active && command.activation == activation

    private fun flush() {
        val sink = send ?: return
        val size = desired ?: return // Initial layout may arrive after attachment.
        if (size != sent) {
            sink(Command.Resize(size))
            sent = size
        }
        if (active && !claimed) {
            sink(Command.Claim(activation))
            claimed = true
        }
    }
}
