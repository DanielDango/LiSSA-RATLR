/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import edu.kit.kastel.sdq.lissa.ratlr.Statistics;

/**
 * Test class for Statistics utility methods.
 *
 * @author GitHub Copilot (Claude Sonnet 4.5)
 */
class StatisticsTest {

    @Test
    void testEscapeMarkdownWithBackslash() {
        String input = "Text with \\ backslash";
        String expected = "Text with \\\\ backslash";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithBacktick() {
        String input = "Code `example`";
        String expected = "Code \\`example\\`";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithAsterisk() {
        String input = "Bold *text*";
        String expected = "Bold \\*text\\*";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithUnderscore() {
        String input = "Italic _text_";
        String expected = "Italic \\_text\\_";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithBraces() {
        String input = "Braces {test}";
        String expected = "Braces \\{test\\}";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithBrackets() {
        String input = "Link [text]";
        String expected = "Link \\[text\\]";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithParentheses() {
        String input = "Parentheses (text)";
        String expected = "Parentheses \\(text\\)";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithHash() {
        String input = "Header #1";
        String expected = "Header \\#1";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithPlus() {
        String input = "Plus + sign";
        String expected = "Plus \\+ sign";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithMinus() {
        String input = "Minus - sign";
        String expected = "Minus \\- sign";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithDot() {
        String input = "List item.";
        String expected = "List item\\.";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithExclamation() {
        String input = "Image !";
        String expected = "Image \\!";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithPipe() {
        String input = "Table | cell";
        String expected = "Table \\| cell";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithGreaterThan() {
        String input = "Quote > text";
        String expected = "Quote \\> text";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithMultipleSpecialChars() {
        String input = "*Bold* _italic_ `code`";
        String expected = "\\*Bold\\* \\_italic\\_ \\`code\\`";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithNoSpecialChars() {
        String input = "Plain text without special characters";
        assertEquals(input, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithEmptyString() {
        String input = "";
        assertEquals(input, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithNewline() {
        String input = "Text with\nnewline";
        String expected = "\n```\nText with\nnewline\n```";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithLongText() {
        String input = "A".repeat(81);
        String expected = "\n```\n" + input + "\n```";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWith80Characters() {
        String input = "A".repeat(80);
        assertEquals(input, Statistics.escapeMarkdown(input));
    }

    @Test
    void testEscapeMarkdownWithAllSpecialChars() {
        String input = "\\`*_{}[]()#+-.!|>";
        String expected = "\\\\\\`\\*\\_\\{\\}\\[\\]\\(\\)\\#\\+\\-\\.\\!\\|\\>";
        assertEquals(expected, Statistics.escapeMarkdown(input));
    }
}
