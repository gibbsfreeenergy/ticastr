package com.wzh.blog.util;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * Sanitizes user-provided text before it is persisted or rendered with
 * {@code v-html}. Regex-based tag stripping is deliberately avoided because
 * it cannot safely parse malformed HTML or URL protocols.
 */
public final class HTMLUtils {

    private static final PolicyFactory RICH_TEXT_POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "b", "em", "i", "u", "s", "del", "blockquote",
                    "pre", "code", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6", "hr",
                    "a", "img", "table", "thead", "tbody", "tr", "th", "td")
            .allowAttributes("href", "title").onElements("a")
            .allowAttributes("src", "alt", "title", "width", "height").onElements("img")
            .allowAttributes("colspan", "rowspan").onElements("th", "td")
            .allowUrlProtocols("http", "https")
            .requireRelNofollowOnLinks()
            .toFactory();

    private static final PolicyFactory PLAIN_TEXT_POLICY = new HtmlPolicyBuilder().toFactory();

    private HTMLUtils() {
    }

    /**
     * Keeps only the explicitly allowed rich-text markup and URL protocols.
     */
    public static String sanitizeRichText(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        return RICH_TEXT_POLICY.sanitize(source);
    }

    /**
     * Removes every HTML element for values that are displayed as text.
     */
    public static String sanitizePlainText(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        return PLAIN_TEXT_POLICY.sanitize(source).strip();
    }
}
