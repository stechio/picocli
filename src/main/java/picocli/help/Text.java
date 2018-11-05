package picocli.help;

import java.util.ArrayList;
import java.util.List;

import picocli.help.Ansi.IStyle;
import picocli.help.Ansi.Style;

/**
 * Encapsulates rich text with styles and colors.
 * <p>
 * Text objects may be constructed with strings containing markup like
 * '{@code @|bg(red),white,underline some text|@}': this class converts that markup to ANSI escape
 * codes.
 * </p>
 * <p>
 * Markup is transparent to text indexing as, internally, plain text representation is kept separate
 * from style mapping.
 * </p>
 */
public class Text implements Appendable, Cloneable {
    private static class StyledChunk {
        private int startIndex, length;
        private String startStyles, endStyles;

        StyledChunk(int start, int len, String style1, String style2) {
            startIndex = start;
            length = len;
            startStyles = style1;
            endStyles = style2;
        }

        StyledChunk withStartIndex(int newStart) {
            return new StyledChunk(newStart, length, startStyles, endStyles);
        }
    }

    private static final String StyledChunkEndTag = "|@";
    private static final String StyledChunkInternalSeparator = " ";
    private static final String StyledChunkStartTag = "@|";

    int maxLength;

    private final Ansi ansi;
    /**
     * Whether the buffer mapping (substring) doesn't reach its actual ending.
     */
    private boolean fragmented;
    private int from;
    private int length;
    private StringBuilder plainTextBuffer = new StringBuilder();
    private List<StyledChunk> styledChunks = new ArrayList<>();

    /**
     * Constructs a Text with the specified string, which may contain markup like
     * {@code @|bg(red),white,underline some text|@}.
     * 
     * @param ansi
     * @param styledText
     *            Marked-up string.
     */
    public Text(Ansi ansi, CharSequence styledText) {
        this(ansi, -1);

        append(styledText);
    }

    /**
     * Constructs a Text with the specified literal string.
     * 
     * @param ansi
     * @param plainText
     *            String without markup.
     * @param styles
     *            Styles to apply to the whole string.
     */
    public Text(Ansi ansi, CharSequence plainText, List<IStyle> styles) {
        this(ansi, -1);

        if (plainText.length() > 0) {
            addStyledChunk(0, plainText.length(), styles.toArray(new IStyle[styles.size()]));
            plainTextBuffer.append(plainText);
            length = plainTextBuffer.length();
        }
    }

    /**
     * Constructs a Text with the specified max length (for use in a TextTable Column).
     * 
     * @param ansi
     * @param maxLength
     *            max length of this text
     */
    public Text(Ansi ansi, int maxLength) {
        this.ansi = ansi;
        this.maxLength = maxLength;
    }

    @Override
    public Text append(char c) {
        if (fragmented) {
            defragment();
        }
        plainTextBuffer.append(c);
        length++;
        return this;
    }

    @Override
    public Text append(CharSequence styledText) {
        if (styledText.length() > 0) {
            if (fragmented) {
                defragment();
            }

            String styledTextString = styledText.toString();
            int chunkStart = 0;
            while (true) {
                /*
                 * NOTE: <STYLED_CHUNK> ::= <START_TAG> <STYLES> <SPACE> <PLAIN_TEXT> <END_TAG>
                 */
                int styledChunkStart = styledTextString.indexOf(StyledChunkStartTag, chunkStart);
                // No more styled chunks?
                if (styledChunkStart == -1) {
                    // Add terminal plain chunk!
                    plainTextBuffer.append(
                            styledTextString.substring(chunkStart, styledTextString.length()));
                    break;
                }

                // Add intermediate plain chunk!
                plainTextBuffer.append(styledTextString.substring(chunkStart, styledChunkStart));

                int styledChunkEnd = styledTextString.indexOf(StyledChunkEndTag, styledChunkStart);
                if (styledChunkEnd == -1) {
                    /*
                     * NOTE: No conversion without closing tag.
                     */
                    // Add malformed marked-up chunk!
                    plainTextBuffer.append(styledTextString.substring(styledChunkStart));
                    break;
                }

                String styledChunk = styledTextString.substring(styledChunkStart + 2,
                        styledChunkEnd);
                String[] styledChunkParts = styledChunk.split(StyledChunkInternalSeparator, 2);
                // Text exists within the marked-up chunk?
                if (styledChunkParts.length == 2) {
                    // Add marked-up chunk!
                    addStyledChunk(plainTextBuffer.length(), styledChunkParts[1].length(),
                            Style.parse(styledChunkParts[0]));
                    plainTextBuffer.append(styledChunkParts[1]);
                } else {
                    /*
                     * NOTE: No conversion without space separator.
                     */
                    // Add empty marked-up chunk!
                    plainTextBuffer.append(
                            styledTextString.substring(styledChunkStart, styledChunkEnd + 2));
                }

                chunkStart = styledChunkEnd + 2;
            }
            length = plainTextBuffer.length() - from;
        }
        return this;
    }

    @Override
    public Text append(CharSequence styledText, int start, int end) {
        return append(new Text(ansi, styledText).substring(start, end));
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
        plainTextBuffer.append(
                other.plainTextBuffer.toString().substring(other.from, other.from + other.length));
        for (StyledChunk styledChunk : other.styledChunks) {
            int index = length + styledChunk.startIndex - other.from;
            styledChunks.add(styledChunk.withStartIndex(index));
        }
        length = plainTextBuffer.length() - from;
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
        clone.plainTextBuffer = new StringBuilder(plainTextBuffer);
        clone.styledChunks = new ArrayList<>(styledChunks);
        return clone;
    }

    /**
     * Copies the specified substring of this Text into the specified destination, preserving the
     * markup.
     * 
     * @param from
     *            Substring start.
     * @param length
     *            Substring length.
     * @param destination
     *            Target text.
     * @param offset
     *            Padding.
     */
    public void copy(int from, int length, Text destination, int offset) {
        from += this.from /* NOTE: Maps to internal from. */;
        if (destination.length < offset) {
            for (int i = destination.length; i < offset; i++) {
                destination.plainTextBuffer.append(' ');
            }
            destination.length = offset;
        }
        for (StyledChunk styledChunk : styledChunks) {
            destination.styledChunks.add(
                    styledChunk.withStartIndex(styledChunk.startIndex - from + destination.length));
        }
        destination.plainTextBuffer
                .append(plainTextBuffer.toString().substring(from, from + length));
        destination.length = destination.plainTextBuffer.length() - destination.from;
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
        boolean trailingEmptyString = plainTextBuffer.length() == 0;
        int start = 0, end = 0;
        for (int i = 0; i < plainTextBuffer.length(); i++, end = i) {
            char c = plainTextBuffer.charAt(i);
            boolean eol = c == '\n';
            eol |= (c == '\r' && i + 1 < plainTextBuffer.length()
                    && plainTextBuffer.charAt(i + 1) == '\n' && ++i > 0); // \r\n
            eol |= c == '\r';
            if (eol) {
                result.add(this.substring(start, end));
                trailingEmptyString = i == plainTextBuffer.length() - 1;
                start = i + 1;
            }
        }
        if (start < plainTextBuffer.length() || trailingEmptyString) {
            result.add(this.substring(start, plainTextBuffer.length()));
        }
        return result.toArray(new Text[result.size()]);
    }

    /**
     * Returns a new {@code Text} instance that is a substring of this Text.
     * 
     * @param start
     *            Index in the plain text where to start the substring
     * @return a new Text instance that is a substring of this Text.
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
        result.fragmented = result.length < result.plainTextBuffer.length() - result.from;
        return result;
    }

    /**
     * Returns the plain text without any formatting.
     */
    public String toPlainString() {
        return plainTextBuffer.toString().substring(from, from + length);
    }

    /**
     * Returns a String representation of the text with ANSI escape codes embedded, unless ANSI is
     * {@linkplain Ansi#enabled()} not enabled}, in which case the plain text is returned.
     */
    @Override
    public String toString() {
        if (!ansi.enabled())
            return plainTextBuffer.toString().substring(from, from + length);
        if (length == 0)
            return "";
        StringBuilder sb = new StringBuilder(plainTextBuffer.length() + 20 * styledChunks.size());
        StyledChunk current = null;
        int end = Math.min(from + length, plainTextBuffer.length());
        for (int i = from; i < end; i++) {
            StyledChunk styledChunk = findStyledChunkOf(i);
            if (styledChunk != current) {
                if (current != null) {
                    sb.append(current.endStyles);
                }
                if (styledChunk != null) {
                    sb.append(styledChunk.startStyles);
                }
                current = styledChunk;
            }
            sb.append(plainTextBuffer.charAt(i));
        }
        if (current != null) {
            sb.append(current.endStyles);
        }
        return sb.toString();
    }

    private void addStyledChunk(int start, int length, IStyle[] styles) {
        styledChunks.add(new StyledChunk(start, length, Style.on(styles),
                Style.off(Ansi.reverse(styles)) + Style.reset.off()));
    }

    private void defragment() {
        /*
         * NOTE: Buffer is reallocated only if its mapping is noncontiguous to the new appendage.
         */
        if (fragmented) {
            plainTextBuffer = new StringBuilder(
                    plainTextBuffer.toString().substring(from, from + length));
            List<StyledChunk> newStyledChunks = new ArrayList<>();
            for (StyledChunk styledChunk : styledChunks) {
                newStyledChunks.add(styledChunk.withStartIndex(styledChunk.startIndex - from));
            }
            from = 0;
            length = plainTextBuffer.length();
            styledChunks = newStyledChunks;
            fragmented = false;
        }
    }

    private StyledChunk findStyledChunkOf(int index) {
        for (StyledChunk styledChunk : styledChunks) {
            if (index >= styledChunk.startIndex
                    && index < styledChunk.startIndex + styledChunk.length)
                return styledChunk;
        }
        return null;
    }
}