package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Translated from LangChain (Python) to Java
class RecursiveCharacterTextSplitter {
    private final boolean keepSeparator;
    private final int chunkSize;
    private final List<String> separators;
    private final boolean isSeparatorRegex;

    public RecursiveCharacterTextSplitter(List<String> separators, boolean keepSeparator, boolean isSeparatorRegex, int chunkSize) {
        this.keepSeparator = keepSeparator;
        this.chunkSize = chunkSize;
        this.separators = separators;
        this.isSeparatorRegex = isSeparatorRegex;
    }

    public List<String> splitText(String text) {
        return splitText(text, separators);
    }

    private List<String> splitText(String text, List<String> separators) {
        List<String> finalChunks = new ArrayList<>();
        String separator = separators.getLast();
        List<String> newSeparators = new ArrayList<>();
        for (int i = 0; i < separators.size(); i++) {
            String _s = separators.get(i);
            String _separator = isSeparatorRegex ? _s : Pattern.quote(_s);
            if (_s.isEmpty()) {
                separator = _s;
                break;
            }
            if (Pattern.compile(_separator).matcher(text).find()) {
                separator = _s;
                newSeparators = separators.subList(i + 1, separators.size());
                break;
            }
        }
        String _separator = isSeparatorRegex ? separator : Pattern.quote(separator);
        List<String> splits = splitTextWithRegex(text, _separator, keepSeparator);
        List<String> _goodSplits = new ArrayList<>();
        String _sep = keepSeparator ? "" : separator;
        for (String s : splits) {
            if (s.length() < chunkSize) {
                _goodSplits.add(s);
            } else {
                if (!_goodSplits.isEmpty()) {
                    finalChunks.addAll(mergeSplits(_goodSplits, _sep));
                    _goodSplits.clear();
                }
                if (newSeparators.isEmpty()) {
                    finalChunks.add(s);
                } else {
                    finalChunks.addAll(splitText(s, newSeparators));
                }
            }
        }
        if (!_goodSplits.isEmpty()) {
            finalChunks.addAll(mergeSplits(_goodSplits, _sep));
        }
        return finalChunks;
    }

    private List<String> splitTextWithRegex(String text, String separator, boolean keepSeparator) {
        List<String> splits = new ArrayList<>();
        Pattern pattern = Pattern.compile(separator);
        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            if (keepSeparator) {
                splits.add(text.substring(lastEnd, matcher.end()));
            } else {
                splits.add(text.substring(lastEnd, matcher.start()));
            }
            lastEnd = matcher.end();
        }
        splits.add(text.substring(lastEnd));
        splits.removeIf(String::isEmpty);
        return splits;
    }

    public static RecursiveCharacterTextSplitter fromLanguage(Language language, boolean keepSeparator, int chunkSize) {
        List<String> separators = getSeparatorsForLanguage(language);
        return new RecursiveCharacterTextSplitter(separators, keepSeparator, true, chunkSize);
    }

    public static List<String> getSeparatorsForLanguage(Language language) {
        return switch (language) {
            case JAVA -> List.of(
                    "\nclass ",
                    "\npublic ", "\nprotected ", "\nprivate ", "\nstatic ",
                    "\nif ", "\nfor ", "\nwhile ", "\nswitch ", "\ncase ",
                    "\n\n", "\n", " ", ""
            );
            // Add other languages as needed...
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private enum Language {
        JAVA
    }
}