package com.sbi.surakshasathi.feature.messagescan.domain.usecase

import javax.inject.Inject

/**
 * Extracts URLs from message text using a regex-based parser.
 *
 * Domain layer: pure Kotlin, no Android imports.
 * Used by [IncomingMessageMapper] and [RuleBasedClassifier].
 *
 * Extracted URLs are passed to [UrlReputationChecker] in Flow 2.
 */
class ExtractUrlsUseCase
    @Inject
    constructor() {
        /**
         * Returns a list of URLs found in [text].
         * Handles http/https, shortened URLs (bit.ly, tinyurl, etc.), and bare domains.
         */
        operator fun invoke(text: String): List<String> {
            return URL_REGEX.findAll(text)
                .map { it.value.trim() }
                .filter { it.length > 4 }
                .distinct()
                .toList()
        }

        companion object {
            // Matches http(s) URLs and common bare domains
            private val URL_REGEX =
                Regex(
                    """(https?://[^\s<>"']+|www\.[a-zA-Z0-9\-]+\.[a-zA-Z]{2,}[^\s<>"']*)""",
                    setOf(RegexOption.IGNORE_CASE),
                )

            /** Known URL shortener domains — high-risk signal */
            val URL_SHORTENERS =
                setOf(
                    "bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "t.co",
                    "shorturl.at", "cutt.ly", "rb.gy", "clck.ru", "is.gd",
                )

            /** Returns true if the URL uses a known shortener service. */
            fun isShortenerUrl(url: String): Boolean = URL_SHORTENERS.any { url.contains(it, ignoreCase = true) }

            /** Returns true if the URL ends with .apk */
            fun isApkDownloadUrl(url: String): Boolean =
                url.endsWith(".apk", ignoreCase = true) ||
                    url.contains(".apk?", ignoreCase = true)
        }
    }
