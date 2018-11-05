package picocli.help;

import java.util.ArrayList;
import java.util.List;

import picocli.help.Ansi.IStyle;
import picocli.help.Ansi.Style;

/**
 * Encapsulates rich text with styles and colors. Text objects may be constructed with Strings
 * containing markup like {@code @|bg(red),white,underline some text|@}, and this class converts the
 * markup to ANSI escape codes.
 * <p>
 * Internally keeps both an enriched and a plain text representation to allow layout components to
 * calculate text width while remaining unaware of the embedded ANSI escape codes.
 * </p>
 */
public class Text implements Appendable, Cloneable {
    static class StyledSection {
        int startIndex, length;
        String startStyles, endStyles;

        StyledSection(int start, int len, String style1, String style2) {
            startIndex = start;
            length = len;
            startStyles = style1;
            endStyles = style2;
        }

        StyledSection withStartIndex(int newStart) {
            return new StyledSection(newStart, length, startStyles, endStyles);
        }
    }

    private static final String StyledSectionEndTag = "|@";
    private static final String StyledSectionStartTag = "@|";

    int maxLength;

    private final Ansi ansi;
    private int from;
    private int length;
    private StringBuilder plain = new StringBuilder();
    private List<StyledSection> sections = new ArrayList<>();
    /**
     * Whether the buffer mapping (substring) doesn't reach its actual ending.
     */
    private boolean fragmented;

    /**
     * Constructs a Text with the specified max length (for use in a TextTable Column).
     * 
     * @param maxLength
     *            max length of this text
     * @param ansi
     *            TODO
     */
    public Text(Ansi ansi, int maxLength) {
        this.ansi = ansi;
        this.maxLength = maxLength;
    }

    /**
     * Constructs a Text with the specified String, which may contain markup like
     * {@code @|bg(red),white,underline some text|@}.
     * 
     * @param styledText
     *            the string with markup to parse
     * @param ansi
     *            TODO
     */
    public Text(Ansi ansi, String styledText) {
        this(ansi, -1);

        int i = 0;
        while (true) {
            int j = styledText.indexOf(StyledSectionStartTag, i);
            if (j == -1) {
                if (i == 0) {
                    plain.append(styledText);
                    length = plain.length();
                    return;
                }
                plain.append(styledText.substring(i, styledText.length()));
                length = plain.length();
                return;
            }
            plain.append(styledText.substring(i, j));
            int k = styledText.indexOf(StyledSectionEndTag, j);
            if (k == -1) {
                plain.append(styledText);
                length = plain.length();
                return;
            }

            j += 2;
            String spec = styledText.substring(j, k);
            String[] items = spec.split(" ", 2);
            if (items.length == 1) {
                plain.append(styledText);
                length = plain.length();
                return;
            }

            addStyledSection(plain.length(), items[1].length(), Style.parse(items[0]));
            plain.append(items[1]);
            i = k + 2;
        }
    }

    public Text(Ansi ansi, String plainText, List<IStyle> styles) {
        this(ansi, -1);

        if (!plainText.isEmpty()) {
            addStyledSection(0, plainText.length(), styles.toArray(new IStyle[styles.size()]));
            plain.append(plainText);
            length = plain.length();
        }
    }

    @Override
    public Text append(char c) {
        if (fragmented) {
            defragment();
        }
        plain.append(c);
        length++;
        return this;
    }

    @Override
    public Text append(CharSequence value) {
        String valueString = value.toString();
        if (valueString.indexOf(StyledSectionStartTag) < 0) {
            if (fragmented) {
                defragment();
            }
            plain.append(valueString);
            length += valueString.length();
            return this;
        } else
            return append(new Text(ansi, valueString));
    }

    @Override
    public Text append(CharSequence csq, int start, int end) {
        return append(csq.subSequence(start, end));
    }

    /**
     * Returns a copy of this {@code Text} instance with the specified text concatenated to the end.
     * Does not modify this instance!
     * 
     * @param other
     *            the text to concatenate to the end of this Text
     * @return a new Text instance
     * @since 3.0
     */
    public Text append(Text other) {
        if (fragmented) {
            defragment();
        }
        plain.append(other.plain.toString().substring(other.from, other.from + other.length));
        for (StyledSection section : other.sections) {
            int index = length + section.startIndex - other.from;
            sections.add(section.withStartIndex(index));
        }
        length = plain.length() - from;
        return this;
    }

    /**
     * Deep clone.
     */
    @Override
    public Text clone() {
        Text clone;
        try {
            clone = (Text) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        clone.plain = new StringBuilder(plain);
        clone.sections = new ArrayList<>(sections);
        return clone;
    }

    /**
     * Copies the specified substring of this Text into the specified destination, preserving the
     * markup.
     * 
     * @param from
     *            start of the substring
     * @param length
     *            length of the substring
     * @param destination
     *            destination Text to modify
     * @param offset
     *            indentation (padding)
     */
    public void copy(int from, int length, Text destination, int offset) {
        // Map to internal from!
        from += this.from;
        if (destination.length < offset) {
            for (int i = destination.length; i < offset; i++) {
                destination.plain.append(' ');
            }
            destination.length = offset;
        }
        for (StyledSection section : sections) {
            destination.sections
                    .add(section.withStartIndex(section.startIndex - from + destination.length));
        }
        destination.plain.append(plain.toString().substring(from, from + length));
        destination.length = destination.plain.length();
    }

    @Override
    public boolean equals(Object obj) {
        return toString().equals(String.valueOf(obj));
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public int length() {
        return length;
    }

    public Text[] splitLines() {
        List<Text> result = new ArrayList<>();
        boolean trailingEmptyString = plain.length() == 0;
        int start = 0, end = 0;
        for (int i = 0; i < plain.length(); i++, end = i) {
            char c = plain.charAt(i);
            boolean eol = c == '\n';
            eol |= (c == '\r' && i + 1 < plain.length() && plain.charAt(i + 1) == '\n' && ++i > 0); // \r\n
            eol |= c == '\r';
            if (eol) {
                result.add(this.substring(start, end));
                trailingEmptyString = i == plain.length() - 1;
                start = i + 1;
            }
        }
        if (start < plain.length() || trailingEmptyString) {
            result.add(this.substring(start, plain.length()));
        }
        return result.toArray(new Text[result.size()]);
    }

    /**
     * Returns a new {@code Text} instance that is a substring of this Text. Does not modify this
     * instance!
     * 
     * @param start
     *            index in the plain text where to start the substring
     * @return a new Text instance that is a substring of this Text
     */
    public Text substring(int start) {
        return substring(start, length);
    }

    /**
     * Returns a new {@code Text} instance that is a substring of this Text. Does not modify this
     * instance!
     * 
     * @param start
     *            index in the plain text where to start the substring
     * @param end
     *            index in the plain text where to end the substring
     * @return a new Text instance that is a substring of this Text
     */
    public Text substring(int start, int end) {
        if (start < 0) {
            start = 0;
        }
        if (end > length) {
            end = length;
        }
        Text result;
        try {
            result = (Text) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        result.from = from + start;
        result.length = end - start;
        result.fragmented = result.length < result.plain.length() - result.from;
        return result;
    }

    /**
     * Returns the plain text without any formatting.
     */
    public String toPlainString() {
        return plain.toString().substring(from, from + length);
    }

    /**
     * Returns a String representation of the text with ANSI escape codes embedded, unless ANSI is
     * {@linkplain Ansi#enabled()} not enabled}, in which case the plain text is returned.
     */
    @Override
    public String toString() {
        if (!ansi.enabled())
            return plain.toString().substring(from, from + length);
        if (length == 0)
            return "";
        StringBuilder sb = new StringBuilder(plain.length() + 20 * sections.size());
        StyledSection current = null;
        int end = Math.min(from + length, plain.length());
        for (int i = from; i < end; i++) {
            StyledSection section = findSectionContaining(i);
            if (section != current) {
                if (current != null) {
                    sb.append(current.endStyles);
                }
                if (section != null) {
                    sb.append(section.startStyles);
                }
                current = section;
            }
            sb.append(plain.charAt(i));
        }
        if (current != null) {
            sb.append(current.endStyles);
        }
        return sb.toString();
    }

    private void addStyledSection(int start, int length, IStyle[] styles) {
        sections.add(new StyledSection(start, length, Style.on(styles),
                Style.off(Ansi.reverse(styles)) + Style.reset.off()));
    }

    private void defragment() {
        /*
         * NOTE: Buffer is reallocated only if its mapping is noncontiguous to the new appendage.
         */
        if (fragmented) {
            plain = new StringBuilder(plain.toString().substring(from, from + length));
            List<StyledSection> newSections = new ArrayList<>();
            for (StyledSection section : sections) {
                newSections.add(section.withStartIndex(section.startIndex - from));
            }
            from = 0;
            length = plain.length();
            sections = newSections;
            fragmented = false;
        }
    }

    private StyledSection findSectionContaining(int index) {
        for (StyledSection section : sections) {
            if (index >= section.startIndex && index < section.startIndex + section.length)
                return section;
        }
        return null;
    }
}