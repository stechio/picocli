package picocli.model;

import picocli.CommandLine;
import picocli.annot.Command;
import picocli.util.Assert;

/**
 * Models parser configuration specification.
 * 
 * @since 3.0
 */
public class ParserSpec {

    /**
     * Constant String holding the default separator between options and option parameters:
     * <code>{@value}</code>.
     */
    public static final String DEFAULT_SEPARATOR = "=";
    private String separator;
    private boolean stopAtUnmatched = false;
    private boolean stopAtPositional = false;
    private String endOfOptionsDelimiter = "--";
    private boolean toggleBooleanFlags = true;
    private boolean overwrittenOptionsAllowed = false;
    private boolean unmatchedArgumentsAllowed = false;
    private boolean expandAtFiles = true;
    private Character atFileCommentChar = '#';
    private boolean posixClusteredShortOptionsAllowed = true;
    private boolean unmatchedOptionsArePositionalParams = false;
    private boolean limitSplit = false;
    private boolean aritySatisfiedByAttachedOptionParam = false;
    boolean collectErrors = false;
    private boolean caseInsensitiveEnumValuesAllowed = false;
    private boolean trimQuotes = false;
    private boolean splitQuotedStrings = false;

    /**
     * Returns the String to use as the separator between options and option parameters.
     * {@code "="} by default, initialized from {@link Command#separator()} if defined.
     */
    public String separator() {
        return (separator == null) ? DEFAULT_SEPARATOR : separator;
    }

    /** @see CommandLine#isStopAtUnmatched() */
    public boolean stopAtUnmatched() {
        return stopAtUnmatched;
    }

    /** @see CommandLine#isStopAtPositional() */
    public boolean stopAtPositional() {
        return stopAtPositional;
    }

    /**
     * @see CommandLine#getEndOfOptionsDelimiter()
     * @since 3.5
     */
    public String endOfOptionsDelimiter() {
        return endOfOptionsDelimiter;
    }

    /** @see CommandLine#isToggleBooleanFlags() */
    public boolean toggleBooleanFlags() {
        return toggleBooleanFlags;
    }

    /** @see CommandLine#isOverwrittenOptionsAllowed() */
    public boolean overwrittenOptionsAllowed() {
        return overwrittenOptionsAllowed;
    }

    /** @see CommandLine#isUnmatchedArgumentsAllowed() */
    public boolean unmatchedArgumentsAllowed() {
        return unmatchedArgumentsAllowed;
    }

    /** @see CommandLine#isExpandAtFiles() */
    public boolean expandAtFiles() {
        return expandAtFiles;
    }

    /**
     * @see CommandLine#getAtFileCommentChar()
     * @since 3.5
     */
    public Character atFileCommentChar() {
        return atFileCommentChar;
    }

    /** @see CommandLine#isPosixClusteredShortOptionsAllowed() */
    public boolean posixClusteredShortOptionsAllowed() {
        return posixClusteredShortOptionsAllowed;
    }

    /**
     * @see CommandLine#isCaseInsensitiveEnumValuesAllowed()
     * @since 3.4
     */
    public boolean caseInsensitiveEnumValuesAllowed() {
        return caseInsensitiveEnumValuesAllowed;
    }

    /**
     * @see CommandLine#isTrimQuotes()
     * @since 3.7
     */
    public boolean trimQuotes() {
        return trimQuotes;
    }

    /**
     * @see CommandLine#isSplitQuotedStrings()
     * @since 3.7
     */
    public boolean splitQuotedStrings() {
        return splitQuotedStrings;
    }

    /** @see CommandLine#isUnmatchedOptionsArePositionalParams() */
    public boolean unmatchedOptionsArePositionalParams() {
        return unmatchedOptionsArePositionalParams;
    }

    boolean splitFirst() {
        return limitSplit();
    }

    /**
     * Returns true if arguments should be split first before any further processing and the
     * number of parts resulting from the split is limited to the max arity of the argument.
     */
    public boolean limitSplit() {
        return limitSplit;
    }

    /**
     * Returns true if options with attached arguments should not consume subsequent
     * arguments and should not validate arity.
     */
    public boolean aritySatisfiedByAttachedOptionParam() {
        return aritySatisfiedByAttachedOptionParam;
    }

    /**
     * Returns true if exceptions during parsing should be collected instead of thrown.
     * Multiple errors may be encountered during parsing. These can be obtained from
     * {@link ParseResult#errors()}.
     * 
     * @since 3.2
     */
    public boolean collectErrors() {
        return collectErrors;
    }

    /**
     * Sets the String to use as the separator between options and option parameters.
     * 
     * @return this ParserSpec for method chaining
     */
    public ParserSpec separator(String separator) {
        this.separator = separator;
        return this;
    }

    /** @see CommandLine#setStopAtUnmatched(boolean) */
    public ParserSpec stopAtUnmatched(boolean stopAtUnmatched) {
        this.stopAtUnmatched = stopAtUnmatched;
        return this;
    }

    /** @see CommandLine#setStopAtPositional(boolean) */
    public ParserSpec stopAtPositional(boolean stopAtPositional) {
        this.stopAtPositional = stopAtPositional;
        return this;
    }

    /**
     * @see CommandLine#setEndOfOptionsDelimiter(String)
     * @since 3.5
     */
    public ParserSpec endOfOptionsDelimiter(String delimiter) {
        this.endOfOptionsDelimiter = Assert.notNull(delimiter, "end-of-options delimiter");
        return this;
    }

    /** @see CommandLine#setToggleBooleanFlags(boolean) */
    public ParserSpec toggleBooleanFlags(boolean toggleBooleanFlags) {
        this.toggleBooleanFlags = toggleBooleanFlags;
        return this;
    }

    /** @see CommandLine#setOverwrittenOptionsAllowed(boolean) */
    public ParserSpec overwrittenOptionsAllowed(boolean overwrittenOptionsAllowed) {
        this.overwrittenOptionsAllowed = overwrittenOptionsAllowed;
        return this;
    }

    /** @see CommandLine#setUnmatchedArgumentsAllowed(boolean) */
    public ParserSpec unmatchedArgumentsAllowed(boolean unmatchedArgumentsAllowed) {
        this.unmatchedArgumentsAllowed = unmatchedArgumentsAllowed;
        return this;
    }

    /** @see CommandLine#setExpandAtFiles(boolean) */
    public ParserSpec expandAtFiles(boolean expandAtFiles) {
        this.expandAtFiles = expandAtFiles;
        return this;
    }

    /**
     * @see CommandLine#setAtFileCommentChar(Character)
     * @since 3.5
     */
    public ParserSpec atFileCommentChar(Character atFileCommentChar) {
        this.atFileCommentChar = atFileCommentChar;
        return this;
    }

    /** @see CommandLine#setPosixClusteredShortOptionsAllowed(boolean) */
    public ParserSpec posixClusteredShortOptionsAllowed(
            boolean posixClusteredShortOptionsAllowed) {
        this.posixClusteredShortOptionsAllowed = posixClusteredShortOptionsAllowed;
        return this;
    }

    /**
     * @see CommandLine#setCaseInsensitiveEnumValuesAllowed(boolean)
     * @since 3.4
     */
    public ParserSpec caseInsensitiveEnumValuesAllowed(
            boolean caseInsensitiveEnumValuesAllowed) {
        this.caseInsensitiveEnumValuesAllowed = caseInsensitiveEnumValuesAllowed;
        return this;
    }

    /**
     * @see CommandLine#setTrimQuotes(boolean)
     * @since 3.7
     */
    public ParserSpec trimQuotes(boolean trimQuotes) {
        this.trimQuotes = trimQuotes;
        return this;
    }

    /**
     * @see CommandLine#setSplitQuotedStrings(boolean)
     * @since 3.7
     */
    public ParserSpec splitQuotedStrings(boolean splitQuotedStrings) {
        this.splitQuotedStrings = splitQuotedStrings;
        return this;
    }

    /** @see CommandLine#setUnmatchedOptionsArePositionalParams(boolean) */
    public ParserSpec unmatchedOptionsArePositionalParams(
            boolean unmatchedOptionsArePositionalParams) {
        this.unmatchedOptionsArePositionalParams = unmatchedOptionsArePositionalParams;
        return this;
    }

    /**
     * Sets whether exceptions during parsing should be collected instead of thrown.
     * Multiple errors may be encountered during parsing. These can be obtained from
     * {@link ParseResult#errors()}.
     * 
     * @since 3.2
     */
    public ParserSpec collectErrors(boolean collectErrors) {
        this.collectErrors = collectErrors;
        return this;
    }

    /**
     * Returns true if options with attached arguments should not consume subsequent
     * arguments and should not validate arity.
     */
    public ParserSpec aritySatisfiedByAttachedOptionParam(boolean newValue) {
        aritySatisfiedByAttachedOptionParam = newValue;
        return this;
    }

    /**
     * Sets whether arguments should be {@linkplain ArgSpec#splitRegex() split} first before
     * any further processing. If true, the original argument will only be split into as
     * many parts as allowed by max arity.
     */
    public ParserSpec limitSplit(boolean limitSplit) {
        this.limitSplit = limitSplit;
        return this;
    }

    void initSeparator(String value) {
        if (Model.initializable(separator, value, DEFAULT_SEPARATOR)) {
            separator = value;
        }
    }

    void updateSeparator(String value) {
        if (Model.isNonDefault(value, DEFAULT_SEPARATOR)) {
            separator = value;
        }
    }

    public String toString() {
        return String.format(
                "posixClusteredShortOptionsAllowed=%s, stopAtPositional=%s, stopAtUnmatched=%s, "
                        + "separator=%s, overwrittenOptionsAllowed=%s, unmatchedArgumentsAllowed=%s, expandAtFiles=%s, "
                        + "atFileCommentChar=%s, endOfOptionsDelimiter=%s, limitSplit=%s, aritySatisfiedByAttachedOptionParam=%s, "
                        + "toggleBooleanFlags=%s, unmatchedOptionsArePositionalParams=%s, collectErrors=%s,"
                        + "caseInsensitiveEnumValuesAllowed=%s, trimQuotes=%s, splitQuotedStrings=%s",
                posixClusteredShortOptionsAllowed, stopAtPositional, stopAtUnmatched,
                separator, overwrittenOptionsAllowed, unmatchedArgumentsAllowed,
                expandAtFiles, atFileCommentChar, endOfOptionsDelimiter, limitSplit,
                aritySatisfiedByAttachedOptionParam, toggleBooleanFlags,
                unmatchedOptionsArePositionalParams, collectErrors,
                caseInsensitiveEnumValuesAllowed, trimQuotes, splitQuotedStrings);
    }

    void initFrom(ParserSpec settings) {
        separator = settings.separator;
        stopAtUnmatched = settings.stopAtUnmatched;
        stopAtPositional = settings.stopAtPositional;
        endOfOptionsDelimiter = settings.endOfOptionsDelimiter;
        toggleBooleanFlags = settings.toggleBooleanFlags;
        overwrittenOptionsAllowed = settings.overwrittenOptionsAllowed;
        unmatchedArgumentsAllowed = settings.unmatchedArgumentsAllowed;
        expandAtFiles = settings.expandAtFiles;
        atFileCommentChar = settings.atFileCommentChar;
        posixClusteredShortOptionsAllowed = settings.posixClusteredShortOptionsAllowed;
        unmatchedOptionsArePositionalParams = settings.unmatchedOptionsArePositionalParams;
        limitSplit = settings.limitSplit;
        aritySatisfiedByAttachedOptionParam = settings.aritySatisfiedByAttachedOptionParam;
        collectErrors = settings.collectErrors;
        caseInsensitiveEnumValuesAllowed = settings.caseInsensitiveEnumValuesAllowed;
        trimQuotes = settings.trimQuotes;
        splitQuotedStrings = settings.splitQuotedStrings;
    }
}
