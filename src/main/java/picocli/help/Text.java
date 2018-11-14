package picocli.help;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import picocli.help.Ansi.IStyle;
import picocli.help.Ansi.Style;
import picocli.util.Utils;

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
public class Text implements Appendable, CharSequence, Cloneable {
    private static class StyledChunk implements Cloneable {
        /**
         * Start index within the text buffer.
         */
        private int index;
        /**
         * Length within the text buffer.
         */
        private int length;
        /**
         * Styles applied to this text chunk.
         */
        private String styles;
        /**
         * Styles applied after this text chunk.
         */
        private String exitStyles;

        StyledChunk(int index, int length, String styles, String exitStyles) {
            this.index = index;
            this.length = length;
            this.styles = styles;
            this.exitStyles = exitStyles;
        }

        @Override
        public StyledChunk clone() {
            try {
                return (StyledChunk) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        public StyledChunk withIndex(int value) {
            this.index = value;
            return this;
        }
    }

    private static final String StyledChunkEndTag = "|@";
    private static final String StyledChunkInternalSeparator = " ";
    private static final String StyledChunkStartTag = "@|";

    int maxLength;

    private final Ansi ansi;
    /**
     * Whether this object is a shallow clone sharing the same buffer with its origin.
     */
    private boolean derived;
    private int from;
    private int length;
    private StringBuilder plainTextBuffer = new StringBuilder();
    private List<StyledChunk> styledChunks = new ArrayList<>();

    public Text(Ansi ansi) {
        this(ansi, -1);
    }

    /**
     * Constructs a Text with the specified string, which may contain markup like
     * {@code @|bg(red),white,underline some text|@}.
     * 
     * @param ansi
     * @param styledText
     *            Marked-up string.
     */
    public Text(Ansi ansi, CharSequence styledText) {
        this(ansi);

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
        this(ansi);

        if (plainText.length() > 0) {
            if (Utils.isNotEmptyAtAll(styles)) {
                addStyledChunk(0, plainText.length(), styles.toArray(new IStyle[styles.size()]));
            }
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
        if (derived) {
            detach();
        }
        plainTextBuffer.append(c);
        length++;
        return this;
    }

    @Override
    public Text append(CharSequence styledText) {
        if (styledText.length() > 0) {
            if (derived) {
                detach();
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
                    plainTextBuffer.append(styledTextString.substring(chunkStart));
                    break;
                }

                // Add intermediate plain chunk!
                plainTextBuffer.append(styledTextString.substring(chunkStart, styledChunkStart));

                int styledChunkEnd = styledTextString.indexOf(StyledChunkEndTag, styledChunkStart);
                if (styledChunkEnd == -1) {
                    /*
                     * NOTE: No conversion without closing tag.
                     */
                    // Add malformed terminal marked-up chunk!
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
        return append(new Text(ansi, styledText).subSequence(start, end));
    }

    public Text append(Text other) {
        if (derived) {
            detach();
        }
        plainTextBuffer.append(
                other.plainTextBuffer.toString().substring(other.from, other.from + other.length));
        for (StyledChunk styledChunk : other.styledChunks) {
            int index = length + styledChunk.index - other.from;
            styledChunks.add(styledChunk.clone().withIndex(index));
        }
        length = plainTextBuffer.length() - from;
        return this;
    }

    @Override
    public char charAt(int index) {
        if (index < 0 || index >= length)
            throw new IndexOutOfBoundsException();

        return plainTextBuffer.charAt(from + index);
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
     * @param target
     *            Target text.
     * @param offset
     *            Padding.
     */
    public void copy(int from, int length, Text target, int offset) {
        from += this.from /* NOTE: Maps to internal from. */;
        if (target.length < offset) {
            for (int i = target.length; i < offset; i++) {
                target.plainTextBuffer.append(' ');
            }
            target.length = offset;
        }
        for (StyledChunk styledChunk : styledChunks) {
            target.styledChunks
                    .add(styledChunk.clone().withIndex(styledChunk.index - from + target.length));
        }
        target.plainTextBuffer.append(plainTextBuffer.toString().substring(from, from + length));
        target.length = target.plainTextBuffer.length() - target.from;
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

    @Override
    public int length() {
        return length;
    }

    public Text[] splitLines() {
        List<Text> result = new ArrayList<>();
        boolean trailingEmptyString = length == 0;
        int start = 0, end = 0;
        for (int i = 0; i < length; i++, end = i) {
            char c = charAt(i);
            boolean eol = c == '\n';
            eol |= (c == '\r' && i + 1 < length && charAt(i + 1) == '\n' && ++i > 0); // \r\n
            eol |= c == '\r';
            if (eol) {
                result.add(subSequence(start, end));
                trailingEmptyString = i == length - 1;
                start = i + 1;
            }
        }
        if (start < length || trailingEmptyString) {
            result.add(subSequence(start));
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
    public Text subSequence(int start) {
        return subSequence(start, length);
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
    @Override
    public Text subSequence(int start, int end) {
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
        result.derived = true;
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
        else if (length == 0)
            return StringUtils.EMPTY;

        StringBuilder sb = new StringBuilder(plainTextBuffer.length() + 20 * styledChunks.size());
        StyledChunk current = null;
        int end = Math.min(from + length, plainTextBuffer.length());
        for (int i = from; i < end; i++) {
            StyledChunk styledChunk = findStyledChunkOf(i);
            if (styledChunk != current) {
                if (current != null) {
                    sb.append(current.exitStyles);
                }
                if (styledChunk != null) {
                    sb.append(styledChunk.styles);
                }
                current = styledChunk;
            }
            sb.append(plainTextBuffer.charAt(i));
        }
        if (current != null) {
            sb.append(current.exitStyles);
        }
        return sb.toString();
    }

    private void addStyledChunk(int index, int length, IStyle[] styles) {
        styledChunks.add(new StyledChunk(index, length, Style.on(styles),
                Style.off(Ansi.reverse(styles)) + Style.reset.off()));
    }

    /**
     * Ensures that the internal buffer is ready for appending new text.
     */
    private boolean detach() {
        /*
         * NOTE: In case this object is a shallow clone sharing its internal buffer with the
         * original object, we have to reallocate it in order to apply modifications.
         */
        if (derived) {
            plainTextBuffer = new StringBuilder(
                    plainTextBuffer.toString().substring(from, from + length));
            List<StyledChunk> newStyledChunks = new ArrayList<>();
            for (StyledChunk styledChunk : styledChunks) {
                newStyledChunks.add(styledChunk.clone().withIndex(styledChunk.index - from));
            }
            from = 0;
            length = plainTextBuffer.length();
            styledChunks = newStyledChunks;
            derived = false;
            return true;
        }
        return false;
    }

    /**
     * Retrieves the marked-up chunk whose range includes the given plain text position.
     *
     * @param index
     */
    private StyledChunk findStyledChunkOf(int index) {
        for (StyledChunk styledChunk : styledChunks) {
            if (index >= styledChunk.index && index < styledChunk.index + styledChunk.length)
                return styledChunk;
        }
        return null;
    }
}