package app.safar.core

/** Row kinds of the debug log screen (docs 12.6). */
enum class LogKind { TX, RX, EVENT, ERROR }

/**
 * One debug log row. Frames carry [type] (the message type char) and [hex] (30 bytes, space separated);
 * events carry [name] and [detail]. [ok] is false for frames that failed validation.
 */
data class LogRecord(
    val id: Long,
    val atMillis: Long,
    val kind: LogKind,
    val type: Char? = null,
    val hex: String? = null,
    val ok: Boolean = true,
    val name: String,
    val detail: Map<String, String> = emptyMap(),
)
