/*
   Copyright 2017 Remko Popma

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package picocli;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.Test;

import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.excepts.ParameterException;
import picocli.model.IFactory;
import picocli.model.ITypeConverter;
import picocli.model.TypeConverter;

public class CommandLineTypeConversionTest {

    static class SupportedTypes {
        @Option(names = "-boolean")
        boolean booleanField;
        @Option(names = "-Boolean")
        Boolean aBooleanField;
        @Option(names = "-byte")
        byte byteField;
        @Option(names = "-Byte")
        Byte aByteField;
        @Option(names = "-char")
        char charField;
        @Option(names = "-Character")
        Character aCharacterField;
        @Option(names = "-short")
        short shortField;
        @Option(names = "-Short")
        Short aShortField;
        @Option(names = "-int")
        int intField;
        @Option(names = "-Integer")
        Integer anIntegerField;
        @Option(names = "-long")
        long longField;
        @Option(names = "-Long")
        Long aLongField;
        @Option(names = "-float")
        float floatField;
        @Option(names = "-Float")
        Float aFloatField;
        @Option(names = "-double")
        double doubleField;
        @Option(names = "-Double")
        Double aDoubleField;
        @Option(names = "-String")
        String aStringField;
        @Option(names = "-StringBuilder")
        StringBuilder aStringBuilderField;
        @Option(names = "-CharSequence")
        CharSequence aCharSequenceField;
        @Option(names = "-File")
        File aFileField;
        @Option(names = "-URL")
        URL anURLField;
        @Option(names = "-URI")
        URI anURIField;
        @Option(names = "-Date")
        Date aDateField;
        @Option(names = "-Time")
        Time aTimeField;
        @Option(names = "-BigDecimal")
        BigDecimal aBigDecimalField;
        @Option(names = "-BigInteger")
        BigInteger aBigIntegerField;
        @Option(names = "-Charset")
        Charset aCharsetField;
        @Option(names = "-InetAddress")
        InetAddress anInetAddressField;
        @Option(names = "-Pattern")
        Pattern aPatternField;
        @Option(names = "-UUID")
        UUID anUUIDField;
        @Option(names = "-Currency")
        Currency aCurrencyField;
        @Option(names = "-tz")
        TimeZone aTimeZone;
        @Option(names = "-byteOrder")
        ByteOrder aByteOrder;
        @Option(names = "-Class")
        Class<?> aClass;
        @Option(names = "-Connection")
        Connection aConnection;
        @Option(names = "-Driver")
        Driver aDriver;
        @Option(names = "-Timestamp")
        Timestamp aTimestamp;
        @Option(names = "-NetworkInterface")
        NetworkInterface aNetInterface;
    }

    @Test
    public void testDefaults() {
        SupportedTypes bean = CommandLine.populateCommand(new SupportedTypes());
        assertEquals("boolean", false, bean.booleanField);
        assertEquals("Boolean", null, bean.aBooleanField);
        assertEquals("byte", 0, bean.byteField);
        assertEquals("Byte", null, bean.aByteField);
        assertEquals("char", 0, bean.charField);
        assertEquals("Character", null, bean.aCharacterField);
        assertEquals("short", 0, bean.shortField);
        assertEquals("Short", null, bean.aShortField);
        assertEquals("int", 0, bean.intField);
        assertEquals("Integer", null, bean.anIntegerField);
        assertEquals("long", 0, bean.longField);
        assertEquals("Long", null, bean.aLongField);
        assertEquals("float", 0f, bean.floatField, Float.MIN_VALUE);
        assertEquals("Float", null, bean.aFloatField);
        assertEquals("double", 0d, bean.doubleField, Double.MIN_VALUE);
        assertEquals("Double", null, bean.aDoubleField);
        assertEquals("String", null, bean.aStringField);
        assertEquals("StringBuilder", null, bean.aStringBuilderField);
        assertEquals("CharSequence", null, bean.aCharSequenceField);
        assertEquals("File", null, bean.aFileField);
        assertEquals("URL", null, bean.anURLField);
        assertEquals("URI", null, bean.anURIField);
        assertEquals("Date", null, bean.aDateField);
        assertEquals("Time", null, bean.aTimeField);
        assertEquals("BigDecimal", null, bean.aBigDecimalField);
        assertEquals("BigInteger", null, bean.aBigIntegerField);
        assertEquals("Charset", null, bean.aCharsetField);
        assertEquals("InetAddress", null, bean.anInetAddressField);
        assertEquals("Pattern", null, bean.aPatternField);
        assertEquals("UUID", null, bean.anUUIDField);
        assertEquals("Currency", null, bean.aCurrencyField);
        assertEquals("TimeZone", null, bean.aTimeZone);
        assertEquals("ByteOrder", null, bean.aByteOrder);
        assertEquals("Class", null, bean.aClass);
        assertEquals("NetworkInterface", null, bean.aNetInterface);
        assertEquals("Connection", null, bean.aConnection);
        assertEquals("Driver", null, bean.aDriver);
        assertEquals("Timestamp", null, bean.aTimestamp);
    }

    @Test
    public void testTypeConversionSucceedsForValidInput() throws Exception {
        //Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        SupportedTypes bean = CommandLine.populateCommand(new SupportedTypes(), "-boolean",
                "-Boolean", //
                "-byte", "12", "-Byte", "23", //
                "-char", "p", "-Character", "i", //
                "-short", "34", "-Short", "45", //
                "-int", "56", "-Integer", "67", //
                "-long", "78", "-Long", "89", //
                "-float", "1.23", "-Float", "2.34", //
                "-double", "3.45", "-Double", "4.56", //
                "-String", "abc", "-StringBuilder", "bcd", "-CharSequence", "xyz", //
                "-File", "abc.txt", //
                "-URL", "http://pico-cli.github.io", //
                "-URI", "http://pico-cli.github.io/index.html", //
                "-Date", "2017-01-30", //
                "-Time", "23:59:59", //
                "-BigDecimal", "12345678901234567890.123", //
                "-BigInteger", "123456789012345678901", //
                "-Charset", "UTF8", //
                "-InetAddress", InetAddress.getLocalHost().getHostName(), //
                "-Pattern", "a*b", //
                "-UUID", "c7d51423-bf9d-45dd-a30d-5b16fafe42e2", //
                "-Currency", "EUR", "-tz", "Japan/Tokyo", "-byteOrder", "LITTLE_ENDIAN", "-Class",
                "java.lang.String", "-NetworkInterface", "127.0.0.0", "-Timestamp",
                "2017-12-13 13:59:59.123456789"
        //                ,
        //                "-Connection", "jdbc:derby:testDB;create=false",
        //                "-Driver", "org.apache.derby.jdbc.EmbeddedDriver"
        );
        assertEquals("boolean", true, bean.booleanField);
        assertEquals("Boolean", Boolean.TRUE, bean.aBooleanField);
        assertEquals("byte", 12, bean.byteField);
        assertEquals("Byte", Byte.valueOf((byte) 23), bean.aByteField);
        assertEquals("char", 'p', bean.charField);
        assertEquals("Character", Character.valueOf('i'), bean.aCharacterField);
        assertEquals("short", 34, bean.shortField);
        assertEquals("Short", Short.valueOf((short) 45), bean.aShortField);
        assertEquals("int", 56, bean.intField);
        assertEquals("Integer", Integer.valueOf(67), bean.anIntegerField);
        assertEquals("long", 78L, bean.longField);
        assertEquals("Long", Long.valueOf(89L), bean.aLongField);
        assertEquals("float", 1.23f, bean.floatField, Float.MIN_VALUE);
        assertEquals("Float", Float.valueOf(2.34f), bean.aFloatField);
        assertEquals("double", 3.45, bean.doubleField, Double.MIN_VALUE);
        assertEquals("Double", Double.valueOf(4.56), bean.aDoubleField);
        assertEquals("String", "abc", bean.aStringField);
        assertEquals("StringBuilder type", StringBuilder.class,
                bean.aStringBuilderField.getClass());
        assertEquals("StringBuilder", "bcd", bean.aStringBuilderField.toString());
        assertEquals("CharSequence", "xyz", bean.aCharSequenceField);
        assertEquals("File", new File("abc.txt"), bean.aFileField);
        assertEquals("URL", new URL("http://pico-cli.github.io"), bean.anURLField);
        assertEquals("URI", new URI("http://pico-cli.github.io/index.html"), bean.anURIField);
        assertEquals("Date", new SimpleDateFormat("yyyy-MM-dd").parse("2017-01-30"),
                bean.aDateField);
        assertEquals("Time", new Time(new SimpleDateFormat("HH:mm:ss").parse("23:59:59").getTime()),
                bean.aTimeField);
        assertEquals("BigDecimal", new BigDecimal("12345678901234567890.123"),
                bean.aBigDecimalField);
        assertEquals("BigInteger", new BigInteger("123456789012345678901"), bean.aBigIntegerField);
        assertEquals("Charset", Charset.forName("UTF8"), bean.aCharsetField);
        assertEquals("InetAddress", InetAddress.getByName(InetAddress.getLocalHost().getHostName()),
                bean.anInetAddressField);
        assertEquals("Pattern", Pattern.compile("a*b").pattern(), bean.aPatternField.pattern());
        assertEquals("UUID", UUID.fromString("c7d51423-bf9d-45dd-a30d-5b16fafe42e2"),
                bean.anUUIDField);
        assertEquals("Currency", Currency.getInstance("EUR"), bean.aCurrencyField);
        assertEquals("TimeZone", TimeZone.getTimeZone("Japan/Tokyo"), bean.aTimeZone);
        assertEquals("ByteOrder", ByteOrder.LITTLE_ENDIAN, bean.aByteOrder);
        assertEquals("Class", String.class, bean.aClass);
        assertEquals("NetworkInterface",
                NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.0")),
                bean.aNetInterface);
        assertEquals("Timestamp", Timestamp.valueOf("2017-12-13 13:59:59.123456789"),
                bean.aTimestamp);
        //        assertEquals("Connection", DriverManager.getConnection("jdbc:derby:testDB;create=false"), bean.aConnection);
        //        assertEquals("Driver", DriverManager.getDriver("org.apache.derby.jdbc.EmbeddedDriver"), bean.aDriver);
    }

    @Test
    public void testByteFieldsAreDecimal() {
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-byte", "0x1F", "-Byte", "0x0F");
            fail("Should fail on hex input");
        } catch (ParameterException expected) {
            assertEquals("Invalid value for option '-byte': '0x1F' is not a byte",
                    expected.getMessage());
        }
    }

    @Test
    public void testCustomByteConverterAcceptsHexadecimalDecimalAndOctal() {
        SupportedTypes bean = new SupportedTypes();
        CommandLine commandLine = new CommandLine(bean);
        ITypeConverter<Byte> converter = new TypeConverter<Byte>() {
            @Override
            public Byte modelOf(String s) {
                return Byte.decode(s);
            }
        };
        commandLine.registerConverter(Byte.class, converter);
        commandLine.registerConverter(Byte.TYPE, converter);
        commandLine.parse("-byte", "0x1F", "-Byte", "0x0F");
        assertEquals(0x1F, bean.byteField);
        assertEquals(Byte.valueOf((byte) 0x0F), bean.aByteField);

        commandLine.parse("-byte", "010", "-Byte", "010");
        assertEquals(8, bean.byteField);
        assertEquals(Byte.valueOf((byte) 8), bean.aByteField);

        commandLine.parse("-byte", "34", "-Byte", "34");
        assertEquals(34, bean.byteField);
        assertEquals(Byte.valueOf((byte) 34), bean.aByteField);
    }

    @Test
    public void testShortFieldsAreDecimal() {
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-short", "0xFF", "-Short", "0x6FFE");
            fail("Should fail on hex input");
        } catch (ParameterException expected) {
            assertEquals("Invalid value for option '-short': '0xFF' is not a short",
                    expected.getMessage());
        }
    }

    @Test
    public void testCustomShortConverterAcceptsHexadecimalDecimalAndOctal() {
        SupportedTypes bean = new SupportedTypes();
        CommandLine commandLine = new CommandLine(bean);
        ITypeConverter<Short> shortConverter = new TypeConverter<Short>() {
            @Override
            public Short modelOf(String s) {
                return Short.decode(s);
            }
        };
        commandLine.registerConverter(Short.class, shortConverter);
        commandLine.registerConverter(Short.TYPE, shortConverter);
        commandLine.parse("-short", "0xFF", "-Short", "0x6FFE");
        assertEquals(0xFF, bean.shortField);
        assertEquals(Short.valueOf((short) 0x6FFE), bean.aShortField);

        commandLine.parse("-short", "010", "-Short", "010");
        assertEquals(8, bean.shortField);
        assertEquals(Short.valueOf((short) 8), bean.aShortField);

        commandLine.parse("-short", "34", "-Short", "34");
        assertEquals(34, bean.shortField);
        assertEquals(Short.valueOf((short) 34), bean.aShortField);
    }

    @Test
    public void testIntFieldsAreDecimal() {
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-int", "0xFF", "-Integer", "0xFFFF");
            fail("Should fail on hex input");
        } catch (ParameterException expected) {
            assertEquals("Invalid value for option '-int': '0xFF' is not an int",
                    expected.getMessage());
        }
    }

    @Test
    public void testCustomIntConverterAcceptsHexadecimalDecimalAndOctal() {
        SupportedTypes bean = new SupportedTypes();
        CommandLine commandLine = new CommandLine(bean);
        ITypeConverter<Integer> intConverter = new TypeConverter<Integer>() {
            @Override
            public Integer modelOf(String s) {
                return Integer.decode(s);
            }
        };
        commandLine.registerConverter(Integer.class, intConverter);
        commandLine.registerConverter(Integer.TYPE, intConverter);
        commandLine.parse("-int", "0xFF", "-Integer", "0xFFFF");
        assertEquals(255, bean.intField);
        assertEquals(Integer.valueOf(0xFFFF), bean.anIntegerField);

        commandLine.parse("-int", "010", "-Integer", "010");
        assertEquals(8, bean.intField);
        assertEquals(Integer.valueOf(8), bean.anIntegerField);

        commandLine.parse("-int", "34", "-Integer", "34");
        assertEquals(34, bean.intField);
        assertEquals(Integer.valueOf(34), bean.anIntegerField);
    }

    @Test
    public void testLongFieldsAreDecimal() {
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-long", "0xAABBCC", "-Long",
                    "0xAABBCCDD");
            fail("Should fail on hex input");
        } catch (ParameterException expected) {
            assertEquals("Invalid value for option '-long': '0xAABBCC' is not a long",
                    expected.getMessage());
        }
    }

    @Test
    public void testCustomLongConverterAcceptsHexadecimalDecimalAndOctal() {
        SupportedTypes bean = new SupportedTypes();
        CommandLine commandLine = new CommandLine(bean);
        ITypeConverter<Long> longConverter = new TypeConverter<Long>() {
            @Override
            public Long modelOf(String s) {
                return Long.decode(s);
            }
        };
        commandLine.registerConverter(Long.class, longConverter);
        commandLine.registerConverter(Long.TYPE, longConverter);
        commandLine.parse("-long", "0xAABBCC", "-Long", "0xAABBCCDD");
        assertEquals(0xAABBCC, bean.longField);
        assertEquals(Long.valueOf(0xAABBCCDDL), bean.aLongField);

        commandLine.parse("-long", "010", "-Long", "010");
        assertEquals(8, bean.longField);
        assertEquals(Long.valueOf(8), bean.aLongField);

        commandLine.parse("-long", "34", "-Long", "34");
        assertEquals(34, bean.longField);
        assertEquals(Long.valueOf(34), bean.aLongField);
    }

    @Test
    public void testTimeFormatHHmmSupported() throws ParseException {
        SupportedTypes bean = CommandLine.populateCommand(new SupportedTypes(), "-Time", "23:59");
        assertEquals("Time", new Time(new SimpleDateFormat("HH:mm").parse("23:59").getTime()),
                bean.aTimeField);
    }

    @Test
    public void testTimeFormatHHmmssSupported() throws ParseException {
        SupportedTypes bean = CommandLine.populateCommand(new SupportedTypes(), "-Time",
                "23:59:58");
        assertEquals("Time", new Time(new SimpleDateFormat("HH:mm:ss").parse("23:59:58").getTime()),
                bean.aTimeField);
    }

    @Test
    public void testTimeFormatHHmmssDotSSSSupported() throws ParseException {
        SupportedTypes bean = CommandLine.populateCommand(new SupportedTypes(), "-Time",
                "23:59:58.123");
        assertEquals("Time",
                new Time(new SimpleDateFormat("HH:mm:ss.SSS").parse("23:59:58.123").getTime()),
                bean.aTimeField);
    }

    @Test
    public void testTimeFormatHHmmssCommaSSSSupported() throws ParseException {
        SupportedTypes bean = CommandLine.populateCommand(new SupportedTypes(), "-Time",
                "23:59:58,123");
        assertEquals("Time",
                new Time(new SimpleDateFormat("HH:mm:ss,SSS").parse("23:59:58,123").getTime()),
                bean.aTimeField);
    }

    @Test
    public void testTimeFormatHHmmssSSSInvalidError() throws ParseException {
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-Time", "23:59:58;123");
            fail("Invalid format was accepted");
        } catch (ParameterException expected) {
            assertEquals(
                    "Invalid value for option '-Time': '23:59:58;123' is not a HH:mm[:ss[.SSS]] time",
                    expected.getMessage());
        }
    }

    @Test
    public void testTimeFormatHHmmssDotInvalidError() throws ParseException {
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-Time", "23:59:58.");
            fail("Invalid format was accepted");
        } catch (ParameterException expected) {
            assertEquals(
                    "Invalid value for option '-Time': '23:59:58.' is not a HH:mm[:ss[.SSS]] time",
                    expected.getMessage());
        }
    }

    @Test
    public void testTimeFormatHHmmsssInvalidError() throws ParseException {
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-Time", "23:59:587");
            fail("Invalid format was accepted");
        } catch (ParameterException expected) {
            assertEquals(
                    "Invalid value for option '-Time': '23:59:587' is not a HH:mm[:ss[.SSS]] time",
                    expected.getMessage());
        }
    }

    @Test
    public void testTimeFormatHHmmssColonInvalidError() throws ParseException {
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-Time", "23:59:");
            fail("Invalid format was accepted");
        } catch (ParameterException expected) {
            assertEquals(
                    "Invalid value for option '-Time': '23:59:' is not a HH:mm[:ss[.SSS]] time",
                    expected.getMessage());
        }
    }

    @Test
    public void testDateFormatYYYYmmddInvalidError() throws ParseException {
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-Date", "20170131");
            fail("Invalid format was accepted");
        } catch (ParameterException expected) {
            assertEquals("Invalid value for option '-Date': '20170131' is not a yyyy-MM-dd date",
                    expected.getMessage());
        }
    }

    @Test
    public void testCharConverterInvalidError() throws ParseException {
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-Character", "aa");
            fail("Invalid format was accepted");
        } catch (ParameterException expected) {
            assertEquals("Invalid value for option '-Character': 'aa' is not a single character",
                    expected.getMessage());
        }
        try {
            CommandLine.populateCommand(new SupportedTypes(), "-char", "aa");
            fail("Invalid format was accepted");
        } catch (ParameterException expected) {
            assertEquals("Invalid value for option '-char': 'aa' is not a single character",
                    expected.getMessage());
        }
    }

    @Test
    public void testNumberConvertersInvalidError() {
        parseInvalidValue("-Byte", "aa", "Invalid value for option '-Byte': 'aa' is not a byte");
        parseInvalidValue("-byte", "aa", "Invalid value for option '-byte': 'aa' is not a byte");
        parseInvalidValue("-Short", "aa", "Invalid value for option '-Short': 'aa' is not a short");
        parseInvalidValue("-short", "aa", "Invalid value for option '-short': 'aa' is not a short");
        parseInvalidValue("-Integer", "aa",
                "Invalid value for option '-Integer': 'aa' is not an int");
        parseInvalidValue("-int", "aa", "Invalid value for option '-int': 'aa' is not an int");
        parseInvalidValue("-Long", "aa", "Invalid value for option '-Long': 'aa' is not a long");
        parseInvalidValue("-long", "aa", "Invalid value for option '-long': 'aa' is not a long");
        parseInvalidValue("-Float", "aa", "Invalid value for option '-Float': 'aa' is not a float");
        parseInvalidValue("-float", "aa", "Invalid value for option '-float': 'aa' is not a float");
        parseInvalidValue("-Double", "aa",
                "Invalid value for option '-Double': 'aa' is not a double");
        parseInvalidValue("-double", "aa",
                "Invalid value for option '-double': 'aa' is not a double");
        parseInvalidValue("-BigDecimal", "aa", "java.lang.NumberFormatException");
        parseInvalidValue("-BigInteger", "aa",
                "java.lang.NumberFormatException: For input string: \"aa\"");
    }

    @Test
    public void testURLConvertersInvalidError() {
        parseInvalidValue("-URL", ":::", "java.net.MalformedURLException: no protocol: :::");
    }

    @Test
    public void testURIConvertersInvalidError() {
        parseInvalidValue("-URI", ":::",
                "java.net.URISyntaxException: Expected scheme name at index 0: :::");
    }

    @Test
    public void testCharsetConvertersInvalidError() {
        parseInvalidValue("-Charset", "aa", "java.nio.charset.UnsupportedCharsetException: aa");
    }

    @Test
    public void testInetAddressConvertersInvalidError() {
        parseInvalidValue("-InetAddress", "%$::a?*!a", "java.net.UnknownHostException: %$::a?*!a: Name or service not known");
    }

    @Test
    public void testUUIDConvertersInvalidError() {
        parseInvalidValue("-UUID", "aa",
                "java.lang.IllegalArgumentException: Invalid UUID string: aa");
    }

    @Test
    public void testCurrencyConvertersInvalidError() {
        parseInvalidValue("-Currency", "aa", "java.lang.IllegalArgumentException");
    }

    @Test
    public void testTimeZoneConvertersInvalidError() {
        SupportedTypes bean = CommandLine.populateCommand(new SupportedTypes(), "-tz", "Abc/Def");
        assertEquals(TimeZone.getTimeZone("GMT"), bean.aTimeZone);
    }

    @Test
    public void testNetworkInterfaceConvertersInvalidNull() {
        SupportedTypes bean = CommandLine.populateCommand(new SupportedTypes(), "-NetworkInterface",
                "no such interface");
        assertNull(bean.aNetInterface);
    }

    @Test
    public void testRegexPatternConverterInvalidError() {
        parseInvalidValue("-Pattern", "[[(aa", String.format(
                "java.util.regex.PatternSyntaxException: Unclosed character class near index 4%n"
                        + "[[(aa%n" + "    ^"));
    }

    @Test
    public void testByteOrderConvertersInvalidError() {
        parseInvalidValue("-byteOrder", "aa",
                "Invalid value for option '-byteOrder': 'aa' is not a valid ByteOrder");
    }

    @Test
    public void testClassConvertersInvalidError() {
        parseInvalidValue("-Class", "aa", "java.lang.ClassNotFoundException: aa");
    }

    @Test
    public void testConnectionConvertersInvalidError() {
        parseInvalidValue("-Connection", "aa",
                "Invalid value for option '-Connection': Cannot convert 'aa' to Connection (java.sql.SQLException: No suitable driver)",
                "Invalid value for option '-Connection': Cannot convert 'aa' to Connection (java.sql.SQLException: No suitable driver found for aa)");
    }

    @Test
    public void testDriverConvertersInvalidError() {
        parseInvalidValue("-Driver", "aa",
                "java.sql.SQLException: No suitable driver");
    }

    @Test
    public void testTimestampConvertersInvalidError() {
        parseInvalidValue("-Timestamp", "aa",
                "Invalid value for option '-Timestamp': Cannot convert 'aa' to Timestamp (java.lang.IllegalArgumentException: Timestamp format must be yyyy-mm-dd hh:mm:ss[.fffffffff])",
                "Invalid value for option '-Timestamp': Cannot convert 'aa' to Timestamp (java.lang.IllegalArgumentException: Timestamp format must be yyyy-mm-dd hh:mm:ss.fffffffff)");
    }

    private void parseInvalidValue(String option, String value, String... errorMessage) {
        try {
            SupportedTypes bean = CommandLine.populateCommand(new SupportedTypes(), option, value);
            fail("Invalid format " + value + " was accepted for " + option);
        } catch (ParameterException actual) {
            for (String errMsg : errorMessage) {
                if (actual.getMessage().equals(errMsg)) {
                    return;
                } // that is okay also
            }
            String type = option.substring(1);
            String expected = String.format(
                    "Invalid value for option '%s': Cannot convert '%s' to %s (%s)", option, value,
                    type, errorMessage[0]);
            assertTrue("expected:<" + expected + "> but was:<" + actual.getMessage() + ">",
                    actual.getMessage().startsWith(expected));
        }
    }

    @Test
    public void testRegisterCustomConverter() {
        class Glob {
            public final String glob;

            public Glob(String glob) {
                this.glob = glob;
            }
        }
        class App {
            @Parameters
            Glob globField;
        }
        class GlobConverter extends TypeConverter<Glob> {
            @Override
            public Glob modelOf(String value) {
                return new Glob(value);
            }
        }
        CommandLine commandLine = new CommandLine(new App());
        commandLine.registerConverter(Glob.class, new GlobConverter());

        String[] args = { "a*glob*pattern" };
        List<CommandLine> parsed = commandLine.parse(args);
        assertEquals("not empty", 1, parsed.size());
        assertTrue(parsed.get(0).getCommand() instanceof App);
        App app = (App) parsed.get(0).getCommand();
        assertEquals(args[0], app.globField.glob);
    }

    static class MyGlobConverter extends TypeConverter<MyGlob> {
        @Override
        public MyGlob modelOf(String value) {
            return new MyGlob(value);
        }
    }

    static class MyGlob {
        public final String glob;

        public MyGlob(String glob) {
            this.glob = glob;
        }
    }

    @Test
    public void testAnnotateCustomConverter() {
        class App {
            @Parameters(converter = MyGlobConverter.class)
            MyGlob globField;
        }
        CommandLine commandLine = new CommandLine(new App());

        String[] args = { "a*glob*pattern" };
        List<CommandLine> parsed = commandLine.parse(args);
        assertEquals("not empty", 1, parsed.size());
        assertTrue(parsed.get(0).getCommand() instanceof App);
        App app = (App) parsed.get(0).getCommand();
        assertEquals(args[0], app.globField.glob);
    }

    public static class SqlTypeConverter extends TypeConverter<Integer> {
        @Override
        public Integer modelOf(String value) {
            if ("ARRAY".equals(value)) {
                return Types.ARRAY;
            }
            if ("BIGINT".equals(value)) {
                return Types.BIGINT;
            }
            if ("BINARY".equals(value)) {
                return Types.BINARY;
            }
            if ("BIT".equals(value)) {
                return Types.BIT;
            }
            if ("BLOB".equals(value)) {
                return Types.BLOB;
            }
            if ("BOOLEAN".equals(value)) {
                return Types.BOOLEAN;
            }
            if ("CHAR".equals(value)) {
                return Types.CHAR;
            }
            if ("CLOB".equals(value)) {
                return Types.CLOB;
            }
            return Types.OTHER;
        }
    }

    @Test
    public void testAnnotatedCustomConverterDoesNotConflictWithExistingType() {
        class App {
            @Parameters(index = "0", converter = SqlTypeConverter.class)
            int sqlTypeParam;

            @Parameters(index = "1")
            int normalIntParam;

            @Option(names = "-t", converter = SqlTypeConverter.class)
            int sqlTypeOption;

            @Option(names = "-x")
            int normalIntOption;
        }
        App app = new App();
        CommandLine commandLine = new CommandLine(app);

        String[] args = { "-x", "1234", "-t", "BLOB", "CLOB", "5678" };
        commandLine.parse(args);
        assertEquals(1234, app.normalIntOption);
        assertEquals(Types.BLOB, app.sqlTypeOption);
        assertEquals(Types.CLOB, app.sqlTypeParam);
        assertEquals(5678, app.normalIntParam);
    }

    static class ErrorConverter extends TypeConverter<Integer> {
        @Override
        public Integer modelOf(String value) {
            throw new IllegalStateException("bad converter");
        }
    }

    @Test
    public void testAnnotatedCustomConverterErrorHandling() {
        class App {
            @Parameters(converter = ErrorConverter.class)
            int sqlTypeParam;
        }
        CommandLine commandLine = new CommandLine(new App());
        try {
            commandLine.parse("anything");
        } catch (ParameterException ex) {
            assertEquals(
                    "Invalid value for positional parameter at index 0..* (<sqlTypeParam>): Cannot convert 'anything' to int (java.lang.IllegalStateException: bad converter)",
                    ex.getMessage());
        }
    }

    static class CustomConverter extends TypeConverter<Integer> {
        @Override
        public Integer modelOf(String value) {
            return Integer.parseInt(value);
        }
    }

    static class Plus23Converter extends TypeConverter<Integer> {
        @Override
        public Integer modelOf(String value) {
            return Integer.parseInt(value) + 23;
        }
    }

    static class Plus23ConverterFactory implements IFactory {
        @SuppressWarnings("unchecked")
        public <T> T create(Class<T> cls) {
            return (T) new Plus23Converter();
        }
    }

    @Test
    public void testAnnotatedCustomConverterFactory() {
        class App {
            @Parameters(converter = CustomConverter.class)
            int converted;
        }
        App app = new App();
        CommandLine commandLine = new CommandLine(app, new Plus23ConverterFactory());
        commandLine.parse("100");
        assertEquals(123, app.converted);
    }

    static class EnumParams {
        @Option(names = "-timeUnit")
        TimeUnit timeUnit;
        @Option(names = "-timeUnitArray", arity = "2")
        TimeUnit[] timeUnitArray;
        @Option(names = "-timeUnitList", type = TimeUnit.class, arity = "3")
        List<TimeUnit> timeUnitList;
    }

    @Test
    public void testEnumTypeConversionSuceedsForValidInput() {
        EnumParams params = CommandLine.populateCommand(new EnumParams(),
                "-timeUnit SECONDS -timeUnitArray MILLISECONDS SECONDS -timeUnitList SECONDS MICROSECONDS NANOSECONDS"
                        .split(" "));
        assertEquals(SECONDS, params.timeUnit);
        assertArrayEquals(new TimeUnit[] { MILLISECONDS, TimeUnit.SECONDS }, params.timeUnitArray);
        List<TimeUnit> expected = new ArrayList<TimeUnit>(
                Arrays.asList(TimeUnit.SECONDS, TimeUnit.MICROSECONDS, TimeUnit.NANOSECONDS));
        assertEquals(expected, params.timeUnitList);
    }

    @Test
    public void testEnumTypeConversionFailsForInvalidInput() {
        try {
            CommandLine.populateCommand(new EnumParams(), "-timeUnit", "xyz");
            fail("Accepted invalid timeunit");
        } catch (Exception ex) {
            String prefix = "Invalid value for option '-timeUnit': expected one of ";
            String suffix = "but was 'xyz'";
            assertEquals(prefix, ex.getMessage().substring(0, prefix.length()));
            assertEquals(suffix, ex.getMessage().substring(
                    ex.getMessage().length() - suffix.length(), ex.getMessage().length()));
        }
    }

    @Test
    public void testEnumTypeConversionIsCaseInsensitiveIfConfigured() {
        EnumParams params = new EnumParams();
        new CommandLine(params).setCaseInsensitiveEnumValuesAllowed(true).parse(
                "-timeUnit sEcONds -timeUnitArray milliSeconds miCroSeConds -timeUnitList SEConds MiCROsEconds nanoSEConds"
                        .split(" "));
        assertEquals(SECONDS, params.timeUnit);
        assertArrayEquals(new TimeUnit[] { MILLISECONDS, TimeUnit.MICROSECONDS },
                params.timeUnitArray);
        List<TimeUnit> expected = new ArrayList<TimeUnit>(
                Arrays.asList(TimeUnit.SECONDS, TimeUnit.MICROSECONDS, TimeUnit.NANOSECONDS));
        assertEquals(expected, params.timeUnitList);
    }

    @Test
    public void testEnumArrayTypeConversionFailsForInvalidInput() {
        try {
            CommandLine.populateCommand(new EnumParams(), "-timeUnitArray", "a", "b");
            fail("Accepted invalid timeunit");
        } catch (Exception ex) {
            String prefix = "Invalid value for option '-timeUnitArray' at index 0 (<timeUnitArray>): expected one of ";
            String suffix = "but was 'a'";
            assertEquals(prefix, ex.getMessage().substring(0, prefix.length()));
            assertEquals(suffix, ex.getMessage().substring(
                    ex.getMessage().length() - suffix.length(), ex.getMessage().length()));
        }
    }

    @Test
    public void testEnumListTypeConversionFailsForInvalidInput() {
        try {
            CommandLine.populateCommand(new EnumParams(), "-timeUnitList", "SECONDS", "b", "c");
            fail("Accepted invalid timeunit");
        } catch (Exception ex) {
            String prefix = "Invalid value for option '-timeUnitList' at index 1 (<timeUnitList>): expected one of ";
            String suffix = " but was 'b'";
            assertEquals(prefix, ex.getMessage().substring(0, prefix.length()));
            assertEquals(suffix, ex.getMessage().substring(
                    ex.getMessage().length() - suffix.length(), ex.getMessage().length()));
        }
    }

    @Test
    public void testArrayOptionParametersAreAlwaysInstantiated() {
        EnumParams params = new EnumParams();
        TimeUnit[] array = params.timeUnitArray;
        new CommandLine(params).parse("-timeUnitArray", "SECONDS", "MILLISECONDS");
        assertNotSame(array, params.timeUnitArray);
    }

    @Test
    public void testListOptionParametersAreInstantiatedIfNull() {
        EnumParams params = new EnumParams();
        assertNull(params.timeUnitList);
        new CommandLine(params).parse("-timeUnitList", "SECONDS", "MICROSECONDS", "MILLISECONDS");
        assertEquals(Arrays.asList(SECONDS, MICROSECONDS, MILLISECONDS), params.timeUnitList);
    }

    @Test
    public void testListOptionParametersAreReusedInstantiatedIfNonNull() {
        EnumParams params = new EnumParams();
        List<TimeUnit> list = new ArrayList<TimeUnit>();
        params.timeUnitList = list;
        new CommandLine(params).parse("-timeUnitList", "SECONDS", "MICROSECONDS", "SECONDS");
        assertEquals(Arrays.asList(SECONDS, MICROSECONDS, SECONDS), params.timeUnitList);
        assertSame(list, params.timeUnitList);
    }

    @Test
    public void testJava7Types() throws Exception {
        if (System.getProperty("java.version").compareTo("1.7.0") < 0) {
            System.out.println(
                    "Unable to verify Java 7 converters on " + System.getProperty("java.version"));
            return;
        }
        CommandLine commandLine = new CommandLine(new EnumParams());
        Map<Class<?>, ITypeConverter<?>> registry = extractRegistry(commandLine);

        verifyReflectedConverter(registry, "java.nio.file.Path", "/tmp/some/directory",
                new File("/tmp/some/directory").toString());
    }

    @Test
    public void testJava8Types() throws Exception {
        CommandLine commandLine = new CommandLine(new EnumParams());
        Map<Class<?>, ITypeConverter<?>> registry = extractRegistry(commandLine);

        if (System.getProperty("java.version").compareTo("1.8.0") < 0) {
            System.out.println(
                    "Unable to verify Java 8 converters on " + System.getProperty("java.version"));
            return;
        }
        verifyReflectedConverter(registry, "java.time.Duration", "P2DT3H4M", "PT51H4M");
        verifyReflectedConverter(registry, "java.time.Instant", "2007-12-03T10:15:30.00Z",
                "2007-12-03T10:15:30Z");
        verifyReflectedConverter(registry, "java.time.LocalDate", "2007-12-03", "2007-12-03");
        verifyReflectedConverter(registry, "java.time.LocalDateTime", "2007-12-03T10:15:30",
                "2007-12-03T10:15:30");
        verifyReflectedConverter(registry, "java.time.LocalTime", "10:15", "10:15");
        verifyReflectedConverter(registry, "java.time.MonthDay", "--12-03", "--12-03");
        verifyReflectedConverter(registry, "java.time.OffsetDateTime", "2007-12-03T10:15:30+01:00",
                "2007-12-03T10:15:30+01:00");
        verifyReflectedConverter(registry, "java.time.OffsetTime", "10:15:30+01:00",
                "10:15:30+01:00");
        verifyReflectedConverter(registry, "java.time.Period", "P1Y2M3D", "P1Y2M3D");
        verifyReflectedConverter(registry, "java.time.Year", "2007", "2007");
        verifyReflectedConverter(registry, "java.time.YearMonth", "2007-12", "2007-12");
        verifyReflectedConverter(registry, "java.time.ZonedDateTime",
                "2007-12-03T10:15:30+01:00[Europe/Paris]",
                "2007-12-03T10:15:30+01:00[Europe/Paris]");
        verifyReflectedConverter(registry, "java.time.ZoneId", "Europe/Paris", "Europe/Paris");
        verifyReflectedConverter(registry, "java.time.ZoneOffset", "+0800", "+08:00");
    }

    private void verifyReflectedConverter(Map<Class<?>, ITypeConverter<?>> registry, String clsName,
            String value, String expectedToString) throws Exception {
        Class<?> cls = Class.forName(clsName);
        ITypeConverter<?> converter = registry.get(cls);
        Object path = converter.modelOf(value);
        assertTrue(clsName, cls.isAssignableFrom(path.getClass()));
        assertEquals(expectedToString, path.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, ITypeConverter<?>> extractRegistry(CommandLine commandLine)
            throws Exception {
        Object interpreter = makeAccessible(CommandLine.class.getDeclaredField("interpreter"))
                .get(commandLine);
        return (Map<Class<?>, ITypeConverter<?>>) makeAccessible(
                interpreter.getClass().getDeclaredField("converterRegistry")).get(interpreter);
    }

    private static Field makeAccessible(Field f) {
        f.setAccessible(true);
        return f;
    }

    static class EmptyValueConverter extends TypeConverter<Short> {
        @Override
        public Short modelOf(String value) {
            return value == null ? -1 : "".equals(value) ? -2 : Short.valueOf(value);
        }
    }

    @Test
    public void testOptionEmptyValue() {
        class Standard {
            @Option(names = "-x", arity = "0..1", description = "may have empty value")
            Short x;
        }

        try {
            CommandLine.populateCommand(new Standard(), "-x");
            fail("Expect exception for Short.valueOf(\"\")");
        } catch (Exception expected) {
            assertEquals("Invalid value for option '-x': '' is not a short", expected.getMessage());
        }

        Standard withValue1 = CommandLine.populateCommand(new Standard(), "-x=987");
        assertEquals(Short.valueOf((short) 987), withValue1.x);

        //------------------
        class CustomConverter {
            @Option(names = "-x", arity = "0..1", description = "may have empty value", converter = EmptyValueConverter.class)
            Short x;
        }

        CustomConverter withoutValue2 = CommandLine.populateCommand(new CustomConverter(), "-x");
        assertEquals(Short.valueOf((short) -2), withoutValue2.x);

        CustomConverter withValue2 = CommandLine.populateCommand(new CustomConverter(), "-x=987");
        assertEquals(Short.valueOf((short) 987), withValue2.x);
    }

    private Map<Class<?>, ITypeConverter<?>> createSampleRegistry() throws Exception {
        CommandLine commandLine = new CommandLine(new EnumParams());
        Map<Class<?>, ITypeConverter<?>> registry = extractRegistry(commandLine);
        System.clearProperty("picocli.converters.excludes");
        return registry;
    }

    @Test
    public void testWithoutExcludes() throws Exception {
        Map<Class<?>, ITypeConverter<?>> registry = createSampleRegistry();

        assertTrue("at least 39 (actual " + registry.size() + ")", registry.size() >= 39);
        assertTrue("java.sql.Time", registry.containsKey(java.sql.Time.class));
        assertTrue("java.sql.Timestamp", registry.containsKey(java.sql.Timestamp.class));
        assertTrue("java.sql.Connection", registry.containsKey(java.sql.Connection.class));
        assertTrue("java.sql.Driver", registry.containsKey(java.sql.Driver.class));
    }

    @Test
    public void testExcludesRegexByPackage() throws Exception {
        System.setProperty("picocli.converters.excludes", "java.sql.*");
        Map<Class<?>, ITypeConverter<?>> registry = createSampleRegistry();

        assertFalse("java.sql.Time", registry.containsKey(java.sql.Time.class));
        assertFalse("java.sql.Timestamp", registry.containsKey(java.sql.Timestamp.class));
        assertFalse("java.sql.Connection", registry.containsKey(java.sql.Connection.class));
        assertFalse("java.sql.Driver", registry.containsKey(java.sql.Driver.class));
    }

    @Test
    public void testExcludesRegex() throws Exception {
        System.setProperty("picocli.converters.excludes", "java.sql.Ti.*");
        Map<Class<?>, ITypeConverter<?>> registry = createSampleRegistry();

        assertFalse("java.sql.Time", registry.containsKey(java.sql.Time.class));
        assertFalse("java.sql.Timestamp", registry.containsKey(java.sql.Timestamp.class));
        assertTrue("java.sql.Connection", registry.containsKey(java.sql.Connection.class));
        assertTrue("java.sql.Driver", registry.containsKey(java.sql.Driver.class));
    }

    @Test
    public void testExcludesCommaSeparatedRegex() throws Exception {
        //System.setProperty("picocli.trace", "DEBUG");
        System.setProperty("picocli.converters.excludes", "java.sql.Time,java.sql.Connection");
        Map<Class<?>, ITypeConverter<?>> registry = createSampleRegistry();

        assertFalse("java.sql.Time", registry.containsKey(java.sql.Time.class));
        assertTrue("java.sql.Timestamp", registry.containsKey(java.sql.Timestamp.class));
        assertFalse("java.sql.Connection", registry.containsKey(java.sql.Connection.class));
        assertTrue("java.sql.Driver", registry.containsKey(java.sql.Driver.class));
    }
}
