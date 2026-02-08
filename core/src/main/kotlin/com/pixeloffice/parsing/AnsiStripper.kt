package com.pixeloffice.parsing

/**
 * ANSI escape code removal utilities.
 */
object AnsiStripper {
    // ANSI escape sequence pattern
    private val ANSI_ESCAPE_PATTERN = Regex(
        """
        \x1b        # ESC character
        (?:         # Non-capturing group for escape types
            \[      # CSI (Control Sequence Introducer)
            [0-9;]* # Parameters
            [A-Za-z] # Final byte
            |
            \]      # OSC (Operating System Command)
            [^\x07\x1b]* # Command content
            (?:\x07|\x1b\\) # String terminator (BEL or ST)
            |
            [PX^_]  # Other escape sequences (DCS, SOS, PM, APC)
            [^\x1b]* # Content
            \x1b\\  # String terminator
            |
            [@-Z\\-_] # Two-character escape sequences
        )
        """.trimIndent().replace("\n", "").replace(" ", "").replace("#.*".toRegex(), ""),
        RegexOption.COMMENTS
    )

    // Simple pattern for most common ANSI codes (including DEC private mode like \e[?2026h)
    private val SIMPLE_ANSI_PATTERN = Regex("\u001B\\[\\??[0-9;]*[A-Za-z]")

    // Additional control characters to strip (except newline, carriage return, tab)
    private val CONTROL_CHARS_PATTERN = Regex("[\u0000-\u0008\u000b\u000c\u000e-\u001f]")

    /**
     * Remove ANSI escape sequences from text.
     *
     * @param text Text potentially containing ANSI escape codes.
     * @return Clean text with all ANSI escape sequences removed.
     */
    fun stripAnsi(text: String): String {
        var result = text

        // Remove simple ANSI escape sequences (most common)
        result = SIMPLE_ANSI_PATTERN.replace(result, "")

        // Remove other control characters
        result = CONTROL_CHARS_PATTERN.replace(result, "")

        return result
    }

    /**
     * Remove ANSI codes and normalize whitespace.
     *
     * @param text Text to clean and normalize.
     * @return Cleaned text with normalized whitespace.
     */
    fun stripAnsiAndNormalize(text: String): String {
        var result = stripAnsi(text)

        // Normalize line endings
        result = result.replace("\r\n", "\n").replace("\r", "\n")

        // Remove trailing whitespace from lines
        val lines = result.split("\n").map { it.trimEnd() }

        return lines.joinToString("\n")
    }
}
