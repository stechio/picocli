package picocli.help;

import java.util.ArrayList;
import java.util.List;

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

    private final Ansi ansi;
    final int maxLength;
    int from;
    int length;
    StringBuilder plain = new StringBuilder();
    List<StyledSection> sections = new ArrayList<>();

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
     * @param input
     *            the string with markup to parse
     * @param ansi
     *            TODO
     */
    public Text(Ansi ansi, String input) {
        this.ansi = ansi;
        maxLength = -1;
        plain.setLength(0);
        int i = 0;

        while (true) {
            int j = input.indexOf("@|", i);
            if (j == -1) {
                if (i == 0) {
                    plain.append(input);
                    length = plain.length();
                    return;
                }
                plain.append(input.substring(i, input.length()));
                length = plain.length();
                return;
            }
            plain.append(input.substring(i, j));
            int k = input.indexOf("|@", j);
            if (k == -1) {
                plain.append(input);
                length = plain.length();
                return;
            }

            j += 2;
            String spec = input.substring(j, k);
            String[] items = spec.split(" ", 2);
            if (items.length == 1) {
                plain.append(input);
                length = plain.length();
                return;
            }

            Ansi.IStyle[] styles = Style.parse(items[0]);
            addStyledSection(plain.length(), items[1].length(), Style.on(styles),
                    Style.off(Ansi.reverse(styles)) + Style.reset.off());
            plain.append(items[1]);
            i = k + 2;
        }
    }

    @Override
    public Text append(char c) {
        prepareAppend();
        plain.append(c);
        length++;
        return this;
    }

    @Override
    public Text append(CharSequence value) {
        String valueString = value.toString();
        if (valueString.indexOf("@|") < 0) {
            prepareAppend();
            plain.append(valueString);
            length += valueString.length();
            return this;
        } else
            return append(new Text(this.ansi, valueString));
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
        prepareAppend();
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

    /**
     * Returns the plain text without any formatting.
     * 
     * @return the plain text without any formatting
     */
    public String plainString() {
        return plain.toString().substring(from, from + length);
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
        Text result;
        try {
            result = (Text) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        result.from = from + start;
        result.length = end - start;
        return result;
    }

    /**
     * Returns a String representation of the text with ANSI escape codes embedded, unless ANSI is
     * {@linkplain Ansi#enabled()} not enabled}, in which case the plain text is returned.
     * 
     * @return a String representation of the text with ANSI escape codes embedded (if enabled)
     */
    @Override
    public String toString() {
        if (!this.ansi.enabled())
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

    private void addStyledSection(int start, int length, String startStyle, String endStyle) {
        sections.add(new StyledSection(start, length, startStyle, endStyle));
    }

    private StyledSection findSectionContaining(int index) {
        for (StyledSection section : sections) {
            if (index >= section.startIndex && index < section.startIndex + section.length)
                return section;
        }
        return null;
    }

    private void prepareAppend() {
        /*
         * NOTE: Buffer is reallocated only if its mapping is noncontiguous with the new appendage.
         */
        if (length < plain.length() - from) {
            plain = new StringBuilder(plain.toString().substring(from, from + length));
            List<StyledSection> newSections = new ArrayList<>();
            for (StyledSection section : sections) {
                newSections.add(section.withStartIndex(section.startIndex - from));
            }
            from = 0;
            length = plain.length();
            sections = newSections;
        }
    }
}