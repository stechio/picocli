package picocli.help;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

import picocli.util.Assert;

/**
 * <p>
 * Responsible for spacing out {@link Text} values according to the {@link Column} definitions the
 * table was created with. Columns have a width, indentation, and an overflow policy that decides
 * what to do if a value is longer than the column's width.
 * </p>
 */
public class TextTable {
    /**
     * Helper class to index positions in a {@code TextTable}.
     * 
     * @since 2.0
     */
    public static class Cell {
        /** Table column index (zero based). */
        public final int column;
        /** Table row index (zero based). */
        public final int row;

        /**
         * Constructs a new Cell with the specified coordinates in the table.
         * 
         * @param column
         *            the zero-based table column
         * @param row
         *            the zero-based table row
         */
        public Cell(int column, int row) {
            this.column = column;
            this.row = row;
        }
    }

    /**
     * Columns define the width, indent (leading number of spaces in a column before the value) and
     * {@linkplain Overflow Overflow} policy of a column in a {@linkplain TextTable TextTable}.
     */
    public static class Column {

        /**
         * Policy for handling text that is longer than the column width: span multiple columns,
         * wrap to the next row, or simply truncate the portion that doesn't fit.
         */
        public enum Overflow {
            TRUNCATE, SPAN, WRAP
        }

        /** Column width in characters */
        public final int width;

        /**
         * Indent (number of empty spaces at the start of the column preceding the text value)
         */
        public final int indent;

        /** Policy that determines how to handle values larger than the column width. */
        public final Column.Overflow overflow;

        public Column(int width, int indent, Column.Overflow overflow) {
            this.width = width;
            this.indent = indent;
            this.overflow = Assert.notNull(overflow, "overflow");
        }
    }

    /**
     * Constructs a {@code TextTable} with the specified columns.
     * 
     * @param ansi
     *            whether to emit ANSI escape codes or not
     * @param columns
     *            columns to construct this TextTable with
     */
    public static TextTable forColumns(Ansi ansi, Column... columns) {
        return new TextTable(ansi, columns);
    }

    /**
     * Constructs a new TextTable with columns with the specified width, all SPANning multiple
     * columns on overflow except the last column which WRAPS to the next row.
     * 
     * @param ansi
     *            whether to emit ANSI escape codes or not
     * @param columnWidths
     *            the width of each table column (all columns have zero indent)
     */
    public static TextTable forColumnWidths(Ansi ansi, int... columnWidths) {
        Column[] columns = new Column[columnWidths.length];
        for (int i = 0; i < columnWidths.length; i++) {
            columns[i] = new Column(columnWidths[i], 0,
                    i == columnWidths.length - 1 ? Column.Overflow.WRAP : Column.Overflow.SPAN);
        }
        return new TextTable(ansi, columns);
    }

    /**
     * Constructs a TextTable with five columns as follows:
     * <ol>
     * <li>required option/parameter marker (width: 2, indent: 0, TRUNCATE on overflow)</li>
     * <li>short option name (width: 2, indent: 0, TRUNCATE on overflow)</li>
     * <li>comma separator (width: 1, indent: 0, TRUNCATE on overflow)</li>
     * <li>long option name(s) (width: 24, indent: 1, SPAN multiple columns on overflow)</li>
     * <li>description line(s) (width: 51, indent: 1, WRAP to next row on overflow)</li>
     * </ol>
     * 
     * @param ansi
     *            whether to emit ANSI escape codes or not
     * @param usageHelpWidth
     *            the total width of the columns combined
     */
    public static TextTable forDefaultColumns(Ansi ansi, int usageHelpWidth) {
        return forDefaultColumns(ansi, Help.defaultOptionsColumnWidth, usageHelpWidth);
    }

    /**
     * Constructs a TextTable with five columns as follows:
     * <ol>
     * <li>required option/parameter marker (width: 2, indent: 0, TRUNCATE on overflow)</li>
     * <li>short option name (width: 2, indent: 0, TRUNCATE on overflow)</li>
     * <li>comma separator (width: 1, indent: 0, TRUNCATE on overflow)</li>
     * <li>long option name(s) (width: 24, indent: 1, SPAN multiple columns on overflow)</li>
     * <li>description line(s) (width: 51, indent: 1, WRAP to next row on overflow)</li>
     * </ol>
     * 
     * @param ansi
     *            whether to emit ANSI escape codes or not
     * @param longOptionsColumnWidth
     *            the width of the long options column
     * @param usageHelpWidth
     *            the total width of the columns combined
     */
    public static TextTable forDefaultColumns(Ansi ansi, int longOptionsColumnWidth,
            int usageHelpWidth) {
        // "* -c, --create                Creates a ...."
        return forColumns(ansi, new Column(2, 0, Column.Overflow.TRUNCATE), // "*"
                new Column(2, 0, Column.Overflow.TRUNCATE), // "-c"
                new Column(1, 0, Column.Overflow.TRUNCATE), // ","
                new Column(longOptionsColumnWidth, 1, Column.Overflow.SPAN), // " --create"
                new Column(usageHelpWidth - longOptionsColumnWidth, 1, Column.Overflow.WRAP)); // " Creates a ..."
    }

    private static int copy(Text value, Text destination, int offset) {
        int length = Math.min(value.length(), destination.maxLength - offset);
        value.copy(0, length, destination, offset);
        return length;
    }

    /** The column definitions of this table. */
    private final Column[] columns;

    /** The {@code char[]} slots of the {@code TextTable} to copy text values into. */
    protected final List<Text> columnValues = new ArrayList<>();

    /** By default, indent wrapped lines by 2 spaces. */
    public int indentWrappedLines = 2;

    private final Ansi ansi;

    private final int tableWidth;

    protected TextTable(Ansi ansi, Column[] columns) {
        this.ansi = Assert.notNull(ansi, "ansi");
        this.columns = Assert.notNull(columns, "columns").clone();
        if (columns.length == 0)
            throw new IllegalArgumentException("At least one column is required");
        int totalWidth = 0;
        for (Column col : columns) {
            totalWidth += col.width;
        }
        tableWidth = totalWidth;
    }

    /**
     * Adds the required {@code char[]} slots for a new row to the {@link #columnValues} field.
     */
    public void addEmptyRow() {
        for (int i = 0; i < columns.length; i++) {
            columnValues.add(new Text(ansi, columns[i].width));
        }
    }

    /**
     * Delegates to {@link #addRowValues(Text...)}.
     * 
     * @param values
     *            the text values to display in each column of the current row
     */
    public void addRowValues(String... values) {
        Text[] array = new Text[values.length];
        for (int i = 0; i < array.length; i++) {
            array[i] = values[i] == null ? Ansi.EMPTY_TEXT : new Text(ansi, values[i]);
        }
        addRowValues(array);
    }

    /**
     * Adds a new {@linkplain TextTable#addEmptyRow() empty row}, then calls
     * {@link TextTable#putValue(int, int, Ansi.Text) putValue} for each of the specified values,
     * adding more empty rows if the return value indicates that the value spanned multiple columns
     * or was wrapped to multiple rows.
     * 
     * @param values
     *            the values to write into a new row in this TextTable
     * @throws IllegalArgumentException
     *             if the number of values exceeds the number of Columns in this table
     */
    public void addRowValues(Text... values) {
        if (values.length > columns.length)
            throw new IllegalArgumentException(
                    values.length + " values don't fit in " + columns.length + " columns");
        addEmptyRow();
        for (int col = 0; col < values.length; col++) {
            int row = rowCount() - 1;// write to last row: previous value may have wrapped to next row
            TextTable.Cell cell = putValue(row, col, values[col]);

            // add row if a value spanned/wrapped and there are still remaining values
            if ((cell.row != row || cell.column != col) && col != values.length - 1) {
                addEmptyRow();
            }
        }
    }

    /**
     * Returns the {@code Text} slot at the specified row and column to write a text value into.
     * 
     * @param row
     *            the row of the cell whose Text to return
     * @param col
     *            the column of the cell whose Text to return
     * @return the Text object at the specified row and column
     * @deprecated use {@link #textAt(int, int)} instead
     */
    @Deprecated
    public Text cellAt(int row, int col) {
        return textAt(row, col);
    }

    /** The column definitions of this table. */
    public Column[] columns() {
        return columns.clone();
    }

    /**
     * Writes the specified value into the cell at the specified row and column and returns the last
     * row and column written to. Depending on the Column's {@link Column#overflow Overflow} policy,
     * the value may span multiple columns or wrap to multiple rows when larger than the column
     * width.
     * 
     * @param row
     *            the target row in the table
     * @param col
     *            the target column in the table to write to
     * @param value
     *            the value to write
     * @return a Cell indicating the position in the table that was last written to (since 2.0)
     * @throws IllegalArgumentException
     *             if the specified row exceeds the table's {@linkplain TextTable#rowCount() row
     *             count}
     * @since 2.0 (previous versions returned a {@code java.awt.Point} object)
     */
    public TextTable.Cell putValue(int row, int col, Text value) {
        if (row > rowCount() - 1)
            throw new IllegalArgumentException(
                    "Cannot write to row " + row + ": rowCount=" + rowCount());
        if (value == null || value.isEmpty())
            return new Cell(col, row);

        Column column = columns[col];
        int indent = column.indent;
        switch (column.overflow) {
            case TRUNCATE:
                copy(value, textAt(row, col), indent);
                return new Cell(col, row);
            case SPAN:
                int startColumn = col;
                do {
                    boolean lastColumn = col == columns.length - 1;
                    int charsWritten = lastColumn
                            ? copy(BreakIterator.getLineInstance(), value, textAt(row, col), indent)
                            : copy(value, textAt(row, col), indent);
                    value = value.substring(charsWritten);
                    indent = 0;
                    if (value.length() > 0) { // value did not fit in column
                        ++col; // write remainder of value in next column
                    }
                    if (value.length() > 0 && col >= columns.length) { // we filled up all columns on this row
                        addEmptyRow();
                        row++;
                        col = startColumn;
                        indent = column.indent + indentWrappedLines;
                    }
                } while (value.length() > 0);
                return new Cell(col, row);
            case WRAP:
                BreakIterator lineBreakIterator = BreakIterator.getLineInstance();
                do {
                    int charsWritten = copy(lineBreakIterator, value, textAt(row, col), indent);
                    value = value.substring(charsWritten);
                    indent = column.indent + indentWrappedLines;
                    if (value.length() > 0) { // value did not fit in column
                        ++row; // write remainder of value in next row
                        addEmptyRow();
                    }
                } while (value.length() > 0);
                return new Cell(col, row);
        }
        throw new IllegalStateException(column.overflow.toString());
    }

    /**
     * Returns the current number of rows of this {@code TextTable}.
     * 
     * @return the current number of rows in this TextTable
     */
    public int rowCount() {
        return columnValues.size() / columns.length;
    }

    /**
     * Returns the {@code Text} slot at the specified row and column to write a text value into.
     * 
     * @param row
     *            the row of the cell whose Text to return
     * @param col
     *            the column of the cell whose Text to return
     * @return the Text object at the specified row and column
     * @since 2.0
     */
    public Text textAt(int row, int col) {
        return columnValues.get(col + (row * columns.length));
    }

    @Override
    public String toString() {
        return toString(new StringBuilder()).toString();
    }

    /**
     * Copies the text representation that we built up from the options into the specified
     * StringBuilder.
     * 
     * @param text
     *            the StringBuilder to write into
     * @return the specified StringBuilder object (to allow method chaining and a more fluid API)
     */
    public StringBuilder toString(StringBuilder text) {
        int columnCount = this.columns.length;
        StringBuilder row = new StringBuilder(tableWidth);
        for (int i = 0; i < columnValues.size(); i++) {
            Text column = columnValues.get(i);
            row.append(column.toString());
            row.append(new String(Help.spaces(columns[i % columnCount].width - column.length())));
            if (i % columnCount == columnCount - 1) {
                int lastChar = row.length() - 1;
                while (lastChar >= 0 && row.charAt(lastChar) == ' ') {
                    lastChar--;
                } // rtrim
                row.setLength(lastChar + 1);
                text.append(row.toString()).append(System.getProperty("line.separator"));
                row.setLength(0);
            }
        }
        return text;
    }

    private int copy(BreakIterator line, Text text, Text columnValue, int offset) {
        // Deceive the BreakIterator to ensure no line breaks after '-' character
        line.setText(text.toPlainString().replace("-", "\u00ff"));
        int done = 0;
        for (int start = line.first(), end = line
                .next(); end != BreakIterator.DONE; start = end, end = line.next()) {
            Text word = text.substring(start, end); //.replace("\u00ff", "-"); // not needed
            if (columnValue.maxLength >= offset + done + word.length()) {
                done += copy(word, columnValue, offset + done); // TODO messages length
            } else {
                break;
            }
        }
        if (done == 0 && text.length() > columnValue.maxLength) {
            // The value is a single word that is too big to be written to the column. Write as much as we can.
            done = copy(text, columnValue, offset);
        }
        return done;
    }
}