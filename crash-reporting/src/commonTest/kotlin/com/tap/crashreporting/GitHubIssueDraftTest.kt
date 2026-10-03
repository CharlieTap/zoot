package com.tap.crashreporting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubIssueDraftTest {
    @Test
    fun shortReportIsCarriedInTheUrl() {
        val report = CrashReport("Crash: a & b #1", "details", "**Bold** é ✓ `code`\n100% & #2 + spaces")
        val draft = GitHubIssueDraft(report, Config)

        assertNull(draft.reportToPaste)
        assertTrue(draft.url.startsWith("https://github.com/owner/game/issues/new?title="))
        val query = parameters(draft.url)
        assertEquals(setOf("title", "body"), query.keys)
        assertEquals(report.title, query["title"])
        assertEquals(report.markdown, query["body"])
    }

    @Test
    fun encodesReservedAndUnicodeCharacters() {
        assertEquals("a%20b%26c%23d%2Be%25f%3Dg%3Fh%2Fi", "a b&c#d+e%f=g?h/i".percentEncoded())
        assertEquals("%C3%A9%E2%9C%93%F0%9F%8E%AE", "é✓🎮".percentEncoded())
        assertEquals("AZaz09-._~", "AZaz09-._~".percentEncoded())
    }

    @Test
    fun longReportIsCopiedInsteadOfTruncated() {
        val markdown = "x".repeat(MAX_ISSUE_URL_LENGTH)
        val report = CrashReport("Crash: memory out of bounds", "details", markdown)
        val draft = GitHubIssueDraft(report, Config)

        assertEquals(markdown, draft.reportToPaste)
        assertTrue(draft.url.length <= MAX_ISSUE_URL_LENGTH)
        assertEquals(PASTE_PROMPT, parameters(draft.url)["body"])
        assertEquals(report.title, parameters(draft.url)["title"])
    }

    @Test
    fun budgetAppliesToTheEncodedUrl() {
        val prefix = "https://github.com/owner/game/issues/new?title=t&body="
        val fits = CrashReport("t", "", "x".repeat(MAX_ISSUE_URL_LENGTH - prefix.length))
        val encodedTooLong = CrashReport("t", "", " ".repeat(MAX_ISSUE_URL_LENGTH - prefix.length))

        assertEquals(MAX_ISSUE_URL_LENGTH, GitHubIssueDraft(fits, Config).url.length)
        assertNull(GitHubIssueDraft(fits, Config).reportToPaste)
        assertEquals(encodedTooLong.markdown, GitHubIssueDraft(encodedTooLong, Config).reportToPaste)
    }

    @Test
    fun usesTheConfiguredRepository() {
        val config = CrashReportingConfig("other", "https://github.com/example/another-game/")
        val draft = GitHubIssueDraft(CrashReport("t", "", "b"), config)

        assertEquals("https://github.com/example/another-game/issues/new?title=t&body=b", draft.url)
    }

    private fun parameters(url: String): Map<String, String> =
        url
            .substringAfter('?')
            .split('&')
            .associate { parameter -> parameter.substringBefore('=') to percentDecoded(parameter.substringAfter('=')) }

    private fun percentDecoded(value: String): String {
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                bytes += value.substring(index + 1, index + 3).toInt(16).toByte()
                index += 3
            } else {
                bytes += value[index].code.toByte()
                index++
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private companion object {
        val Config = CrashReportingConfig("game", "https://github.com/owner/game")
    }
}
