package app.safar.protocol

/*
 * Regex building blocks that behave like JavaScript's on both the JVM and Android (ICU) regex engines.
 * JavaScript `\s` includes Unicode spaces such as U+00A0 and U+202F (Android uses them between a number
 * and its unit); Java `\s` does not, and ICU `\d` matches non-ASCII digits. So the ported patterns use
 * these explicit classes instead of `\s`, `\d` and `\b`.
 */

/** Members of JavaScript's `\s`, for use inside a character class. */
internal const val JS_SPACE = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

/** JavaScript `\s` as a stand-alone class. */
internal const val WS = "[$JS_SPACE]"

/** JavaScript `\d` (ASCII digits only). */
internal const val DIGIT = "[0-9]"

/** Zero-width JavaScript `\b` after a word character: the next character is not a word character. */
internal const val WORD_END = "(?![A-Za-z0-9_])"

/** true for characters in JavaScript's `\s` (and so removed by `String.prototype.trim`). */
internal fun isJsSpace(c: Char): Boolean =
    c in "\t\n\u000B\u000C\r       　﻿" || c in ' '..' '

/** JavaScript `String.prototype.trim`. */
internal fun jsTrim(text: String): String = text.trim(::isJsSpace)
