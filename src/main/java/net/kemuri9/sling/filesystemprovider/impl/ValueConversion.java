/**
 * Copyright 2016 Steven Walters
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kemuri9.sling.filesystemprovider.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.kemuri9.sling.filesystemprovider.Binary;

@SuppressWarnings("unchecked")
final class ValueConversion {

    /** Map of functions of how to convert from class A to class B. */
    private static final TreeMap<Class<?>, Map<Class<?>, Function<?,?>>> FUNCTION_TABLE = new TreeMap<>(Util.COMPARATOR_CLASS);

    /** classes to ignore when looking for intermediary conversion classes */
    private static final Collection<Class<?>> SCRUB_CLASSES = new ArrayList<>();

    private static final SimpleDateFormat SDF_FULL_ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /** slf4j logger */
    private static final Logger log = LoggerFactory.getLogger(ValueConversion.class);

    /**
     * Conversion on values that should be always converted to something else when a type is not specified.
     * @param val the value to convert
     * @return either the provided value, or a converted value
     */
//    static Object convert(Object val) {
//        if (val == null) {
//            return null;
//        }
//        if (val instanceof Binary) {
//            // binaries should always be converted to InputStreams to avoid problems for the user
//            Binary bin = (Binary) val;
//            // otherwise convert it to InputStream - always
//            try {
//                return bin.getStream();
//            } catch (IOException e) {
//                log.error("unable to get InputStream from binary", bin);
//                return null;
//            }
//        }
//        return val;
//    }

    /**
     * A small type-coercing method for the purpose of chaining method references
     * together without casting or intermediate assignments.
     * <A> the origin type
     * <B> the intermediary type
     * <C> the target type
     * @param func1 the first function
     * @param func2 the second function
     * @return {@code func1.andThen(func2)}
     */
    private static <A, B, C> Function<A, C> andThen(Function<A, B> func1, Function<? super B, ? extends C> func2) {
        return func1.andThen(func2);
    }

    /**
     * Convert the provided value into a new target type
     * @param <T> the type of the class
     * @param val the value to convert
     * @param clazz the class type to convert into
     * @return the converted value, or {@code} null if it could not be converted
     */
    static <T> T convert(Object val, Class<T> clazz) {
        // if null, then no conversion necessary
        if (val == null) {
            return null;
        }
        /* as we can't actually convert to primitives with the generic usage,
         * we need to convert to their corresponding wrapper types */
        if (clazz.isPrimitive()) {
            clazz = (Class<T>) Util.primitiveToWrapper(clazz);
        }
        // if already the target, then no change necessary
        if (clazz.isInstance(val)) {
            return clazz.cast(val);
        }
        // arrays need to have the elements converted to the specified type
        if (clazz.isArray()) {
            return (T) convertToArray(val, clazz.getComponentType());
        }

        Class<Object> valClass = (Class<Object>) val.getClass();
        Function<Object, T> valToDesired = findFunction(valClass, clazz);
        if (valToDesired != null) {
            try {
                return valToDesired.apply(val);
            } catch (Exception e) {
                log.trace("failed to apply conversion function on {} to convert to {}", val, clazz.getName(), e);
            }
        }
        // special case all the serializable possibilities
        if (val instanceof Binary && Serializable.class.isAssignableFrom(clazz)) {
            Object newVal = binaryToSerializable((Binary) val);
            if (newVal != null) {
                try {
                    return clazz.cast(newVal);
                } catch (ClassCastException e) {
                    log.error("error casting {} to be {}", newVal, clazz.getName());
                }
            }
        }
        // special case for toString
        if (clazz.equals(String.class)) {
           return clazz.cast(val.toString());
        }
        log.trace("could not find function to convert from {} to {}", valClass, clazz);
        return null;
    }

    /**
     * Convert the specified value into an array of the specified element type.
     * Note that the return type is {@code Object} and not {@code T[]} due
     * to {@code T[]} assuming the {@code Object[]} form and therefore causing some casting issues.
     * @param <T> the type of the class
     * @param val the value(s) to convert
     * @param elemType the element type of the array to convert into
     * @return the converted array.
     */
    private static <T> Object convertToArray(Object val, Class<T> elemType) {
        if (elemType == null) {
            throw new IllegalArgumentException("Unable to convert to array of null type");
        }
        if (elemType.isArray()) {
            throw new IllegalArgumentException("Unable to convert to multidimensional array");
        }
        if (val == null) {
            return null;
        }
        if (val instanceof Collection) {
            Collection<?> col = (Collection<?>) val;
            // can not use the stream.map.toArray lambda sequence here to support the primitive array types
            Object array = Array.newInstance(elemType, col.size());
            int idx = 0;
            for (Iterator<?> iter = col.iterator(); iter.hasNext(); ++idx) {
                Array.set(array, idx, convert(iter.next(), elemType));
            }
            return array;
        }
        if (val instanceof Iterable) {
            val = ((Iterable<?>) val).iterator();
        }
        if (val instanceof Iterator) {
            Iterator<?> iter = (Iterator<?>) val;
            ArrayList<Object> buildup = new ArrayList<>(32); // guess
            // not much benefit to the forEachRemaining lambda here, so don't bother.
            while (iter.hasNext()) {
                Object subVal = convert(iter.next(), elemType);
                buildup.add(subVal);
            }
            Object array = Array.newInstance(elemType, buildup.size());
            for (int idx = 0; idx < buildup.size(); ++idx) {
                Array.set(array, idx, buildup.get(idx));
            }
            return array;
        }
        if (val.getClass().isArray()) {
            // converting from one array type to another
            int arrayLen = Array.getLength(val);
            Object newArr = Array.newInstance(elemType, arrayLen);
            for (int idx = 0; idx < arrayLen; ++idx) {
                Array.set(newArr, idx, convert(Array.get(val, idx), elemType));
            }
            return newArr;
        }
        // single value conversion
        Object array = Array.newInstance(elemType, 1);
        Array.set(array, 0, convert(val, elemType));
        return array;
    }

    private static GregorianCalendar dateToCalendar(Date date) {
        GregorianCalendar newCal = new GregorianCalendar();
        newCal.setTime(date);
        return newCal;
    }

    private static Serializable binaryToSerializable(Binary bin) {
        log.trace("Attempting deserialization converstion of binary");

        Object deserialized = null;
        try (Util.SpecificClassLoadingObjectInputStream objInputStream =
                new Util.SpecificClassLoadingObjectInputStream(Util.getClassLoader(), Util.getBinaryStreamQuietly(bin))) {
            deserialized = objInputStream.readObject();
            objInputStream.close();
            return (Serializable) deserialized;
        } catch (IOException | ClassNotFoundException e) {
            log.error("Error deserializing serializable class data", e);
            return null;
        } catch (ClassCastException e) {
            log.error("deserialized object {} was not serializable",
                    deserialized.getClass().getName());
            return null;
        }
    }

    private static Binary serializableToBinary(Serializable val) {
        try {
            FileBinary temporary = new FileBinary();
            try (ObjectOutputStream output = new ObjectOutputStream(temporary.getOutputStream())) {
                output.writeObject(val);
            } catch (IOException e) {
                log.error("Error serializing class data", e);
                return null;
            }
            return temporary;
        } catch (IOException e) {
            log.error("Error preparing for serialization", e);
        }
        return null;
    }

    /**
     * Convert the {@link Serializable} into an {@link InputStream} of its serialized data
     * @param val the value to serialize into an input stream
     * @return the InputStream representing the binary data
     */
    private static InputStream serializableToInputStream(Serializable val) {
        Util.CopyStream copyStream = new Util.CopyStream(FSPConstants.BUFFER_SIZE);
        try (ObjectOutputStream output = new ObjectOutputStream(copyStream)) {
            output.writeObject(val);
        } catch (IOException e) {
            log.error("Error serializing class data", e);
            return null;
        }
        return copyStream.toInputStream();
    }

    /**
     * Put a function into the conversion table.
     * @param classA the origin class
     * @param classB the target class
     * @param func the function on how to convert from the origin to the target
     */
    private static <A,B> void putFunction(Class<A> classA, Class<B> classB, Function<? super A,? extends B> func) {
        Map<Class<?>, Function<?,?>> subMap = FUNCTION_TABLE.get(classA);
        if (subMap == null) {
            subMap = new TreeMap<>(Util.COMPARATOR_CLASS);
            FUNCTION_TABLE.put(classA, subMap);
        }
        subMap.put(classB, func);
    }

    /**
     * Retrieve the list of Classes that A can convert into
     * @param classA the class to retrieve the classes it can convert into
     * @return the list of Classes that can be converted into. may be empty, but never {@code null}
     */
    private static <A> Collection<Class<?>> getConvertableClasses(Class<A> classA) {
        Map<Class<?>, Function<?,?>> subMap = FUNCTION_TABLE.get(classA);
        return (subMap == null) ? Collections.emptyList() : subMap.keySet();
    }

    /**
     * Perform a lookup against the table for the desired conversion
     * @param classA the class to convert from
     * @param classB the class to convert into
     * @return the function to convert from A to B. {@code null} if one could not be found.
     */
    private static <A,B> Function<A,B> getFunction(Class<A> classA, Class<B> classB) {
        Map<Class<?>, Function<?,?>> subMap = FUNCTION_TABLE.get(classA);
        return (subMap == null) ? null : (Function<A, B>) subMap.get(classB);
    }

    /**
     * Retrieve a function that parses text into a time based type utilizing the provided parser and accessor
     * @param f the formatter to utilize
     * @param query the acessor to utilize
     * @return the generated function
     */
    private static <T extends TemporalAccessor> Function<CharSequence, T> getParseFunction(DateTimeFormatter f, TemporalQuery<T> query) {
        return (CharSequence s) -> { return f.parse(s, query); };
    }

    /**
     * Retrieve the list of Classes that A can convert into, for the purpose of intermediary transforms.
     * @param classA the class to retrieve the classes it can convert into
     * @return the list of Classes that can be converted into. may be empty, but never {@code null}
     */
    private static <A> Collection<Class<?>> getScrubbedConvertableClasses(Class<?> classA) {
        Collection<Class<?>> classes = getConvertableClasses(classA);
        // only copy and make read-write if needed
        if (!Collections.disjoint(classes, SCRUB_CLASSES)) {
            classes = new ArrayList<>(classes);
            classes.removeAll(SCRUB_CLASSES);
        }

        return classes;
    }

    /**
     * Parse a string into a {@link Date} object
     * @param str the string to parse into a Date
     * @return the string to parse
     */
    private static Date stringToDate(String str) {
        try {
            return SDF_FULL_ISO_8601.parse(str);
        } catch (ParseException e) {
            log.debug("failed to parse {} as Date", str);
        }
        return null;
    }

    static {
        // build up our conversion LUT
        putFunction(BigDecimal.class, BigInteger.class, BigDecimal::toBigInteger); // TODO: use toBigIntegerExact?

        putFunction(BigInteger.class, BigDecimal.class, BigDecimal::new);

        putFunction(Binary.class, String.class, Binary::getName);
        putFunction(Binary.class, Long.class, Binary::getLength);
        putFunction(Binary.class, InputStream.class, Util::getBinaryStreamQuietly);
        putFunction(Binary.class, Serializable.class, ValueConversion::binaryToSerializable);

        putFunction(Calendar.class, String.class, SDF_FULL_ISO_8601::format);
        putFunction(Calendar.class, Instant.class, Calendar::toInstant);
        putFunction(Calendar.class, Date.class, Calendar::getTime);

        Function<CharSequence, ZonedDateTime> strToZDT = getParseFunction(DateTimeFormatter.ISO_OFFSET_DATE_TIME, ZonedDateTime::from);
        Function<CharSequence, GregorianCalendar> strToCal = strToZDT.andThen(GregorianCalendar::from);
        putFunction(CharSequence.class, Calendar.class, strToCal);
        putFunction(CharSequence.class, GregorianCalendar.class, strToCal);
        putFunction(CharSequence.class, Instant.class, getParseFunction(DateTimeFormatter.ISO_INSTANT, Instant::from));
        putFunction(CharSequence.class, LocalDate.class, getParseFunction(DateTimeFormatter.ISO_LOCAL_DATE, LocalDate::from));
        putFunction(CharSequence.class, LocalDateTime.class, getParseFunction(DateTimeFormatter.ISO_LOCAL_DATE_TIME, LocalDateTime::from));
        putFunction(CharSequence.class, LocalTime.class, getParseFunction(DateTimeFormatter.ISO_LOCAL_TIME, LocalTime::from));
        putFunction(CharSequence.class, String.class, CharSequence::toString);

        putFunction(CharSequence.class, ZonedDateTime.class, strToZDT);

        putFunction(Date.class, GregorianCalendar.class, ValueConversion::dateToCalendar);
        putFunction(Date.class, Long.class, Date::getTime);
        putFunction(Date.class, String.class, SDF_FULL_ISO_8601::format);

        putFunction(Double.class, BigDecimal.class, BigDecimal::valueOf);

        putFunction(GregorianCalendar.class, ZonedDateTime.class, GregorianCalendar::toZonedDateTime);
        putFunction(GregorianCalendar.class, String.class, andThen(GregorianCalendar::toZonedDateTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME::format));

        putFunction(Instant.class, Date.class, Date::from);
        putFunction(Instant.class, Long.class, Instant::toEpochMilli);
        putFunction(Instant.class, String.class, DateTimeFormatter.ISO_INSTANT::format);
        putFunction(Instant.class, java.sql.Timestamp.class, java.sql.Timestamp::from);

        putFunction(LocalDate.class, java.sql.Date.class, java.sql.Date::valueOf);
        putFunction(LocalDate.class, String.class, DateTimeFormatter.ISO_LOCAL_DATE::format);

        putFunction(LocalDateTime.class, String.class, DateTimeFormatter.ISO_LOCAL_DATE_TIME::format);
        putFunction(LocalDateTime.class, java.sql.Timestamp.class, java.sql.Timestamp::valueOf);

        putFunction(LocalTime.class, java.sql.Time.class, java.sql.Time::valueOf);
        putFunction(LocalTime.class, String.class, DateTimeFormatter.ISO_LOCAL_TIME::format);

        putFunction(Long.class, BigInteger.class, BigInteger::valueOf);
        putFunction(Long.class, BigDecimal.class, BigDecimal::valueOf);
        putFunction(Long.class, java.sql.Date.class, java.sql.Date::new);
        putFunction(Long.class, Date.class, Date::new);
        putFunction(Long.class, java.sql.Time.class, java.sql.Time::new);
        putFunction(Long.class, java.sql.Timestamp.class, java.sql.Timestamp::new);

        putFunction(Number.class, Byte.class, Number::byteValue);
        putFunction(Number.class, Double.class, Number::doubleValue);
        putFunction(Number.class, Float.class, Number::floatValue);
        putFunction(Number.class, Integer.class, Number::intValue);
        putFunction(Number.class, Long.class, Number::longValue);
        putFunction(Number.class, Short.class, Number::shortValue);

        putFunction(Serializable.class, Binary.class, ValueConversion::serializableToBinary);
        putFunction(Serializable.class, InputStream.class, ValueConversion::serializableToInputStream);

        putFunction(String.class, BigDecimal.class, BigDecimal::new);
        putFunction(String.class, BigInteger.class, BigInteger::new);
        putFunction(String.class, Boolean.class, Boolean::parseBoolean);
        putFunction(String.class, Byte.class, Byte::parseByte);
        putFunction(String.class, Date.class, ValueConversion::stringToDate);
        putFunction(String.class, Double.class, Double::parseDouble);
        putFunction(String.class, Float.class, Float::parseFloat);
        putFunction(String.class, Integer.class, Integer::parseInt);
        putFunction(String.class, Long.class, Long::parseLong);
        putFunction(String.class, Short.class, Short::parseShort);
        putFunction(String.class, java.sql.Date.class, java.sql.Date::valueOf);
        putFunction(String.class, java.sql.Time.class, java.sql.Time::valueOf);
        putFunction(String.class, java.sql.Timestamp.class, java.sql.Timestamp::valueOf);

        putFunction(java.sql.Date.class, LocalDate.class, java.sql.Date::toLocalDate);
        putFunction(java.sql.Date.class, String.class, java.sql.Date::toString);

        putFunction(java.sql.Time.class, LocalTime.class, java.sql.Time::toLocalTime);
        putFunction(java.sql.Time.class, String.class, java.sql.Time::toString);

        putFunction(java.sql.Timestamp.class, Instant.class, java.sql.Timestamp::toInstant);
        putFunction(java.sql.Timestamp.class, LocalDateTime.class, java.sql.Timestamp::toLocalDateTime);
        putFunction(java.sql.Timestamp.class, String.class, java.sql.Timestamp::toString);

        putFunction(ZonedDateTime.class, String.class, DateTimeFormatter.ISO_OFFSET_DATE_TIME::format);
        putFunction(ZonedDateTime.class, GregorianCalendar.class, GregorianCalendar::from);
        // finish LUT for conversions

        // ignore the following classes as possible intermediaries for conversions.
        SCRUB_CLASSES.add(String.class);
        SCRUB_CLASSES.add(InputStream.class);
        SCRUB_CLASSES.add(Serializable.class);
        SCRUB_CLASSES.add(Binary.class);
    }

    /**
     * Retrieve the class hierarchy chain for the specified class
     * @param clazz the class to retrieve its hierarchy.
     * @return the hierarchy for the class. it should be noted that the
     * provided class is included in its own hierarchy.
     */
    private static Set<Class<?>> getClassHierarchy(Class<?> clazz) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        fillClassHierarchy(classes, clazz);
        return classes;
    }

    /**
     * fill in the class hierarchy for the specified class
     * @param classes the current hierarchy
     * @param clazz the new class to add into the hierarchy
     */
    private static void fillClassHierarchy(Set<Class<?>> classes, Class<?> clazz) {
        /* this will add classes in an inconsistent order:
         * every hit class is individually traversed in a DFS fashion.
         * this is a far cry from BFS which may make more logical sense from a technical standpoint,
         * but I don't think it actually matters in the end... */
        if (clazz == null)
            return;
        if (!classes.add(clazz)) {
            return;
        }
        fillClassHierarchy(classes, clazz.getSuperclass());
        for (Class<?> intf : clazz.getInterfaces()) {
            fillClassHierarchy(classes, intf);
        }
    }

    /**
     * Find the function that can convert from class A to class B
     * @param classA the origin class
     * @param classB the target class
     * @return the function that can convert from the origin to the target class, using intermediary conversions if applicable.
     * {@code null} if one could not be found
     */
    private static <A,B> Function<A, B> findFunction(Class<A> classA, Class<B> classB) {
        return findFunction(classA, classB, null);
    }

    /**
     * Find the function that can convert from class A to class B, with the current intermediaries being interrogated
     * @param classA the origin class
     * @param classB the target class
     * @param intermediaries the intermediaries being used in interrogation use in converting A to a different B.
     * {@code null} indicates no intermediaries are currently under investigation
     * @return the function that can convert from the origin to the target class. {@code null} if one could not be found
     */
    private static <A,B,Z> Function<A, B> findFunction(Class<A> classA, Class<B> classB,
            Set<Class<?>> intermediaries) {
        // try directly A to B
        Function<?, ?> func = getFunction(classA, classB);
        if (func != null) {
            return (Function<A, B>) func;
        }

        // try by parents of A to B
        Set<Class<?>> classAHeirarchy = getClassHierarchy(classA);
        for (Class<?> classAParent : classAHeirarchy) {
            func = getFunction(classAParent, classB);
            if (func != null) {
                return (Function<A, B>) func;
            }
        }

        // see if B is a super type of something A can convert into
        for (Class<?> canConvertTo : getConvertableClasses(classA)) {
            if (classB.isAssignableFrom(canConvertTo)) {
                func = getFunction(classA, canConvertTo);
                return (Function<A, B>) func;
            }
        }

        // try to find a chain graph that can resolve the desired endpoints.
        // for now only try and find conversion chains via one intermediary.
        if (intermediaries == null || intermediaries.size() < 1) {
            if (intermediaries == null) {
                intermediaries = new LinkedHashSet<>();
            }
            // try by intermediary Z
            for (Class<?> classAParent : classAHeirarchy) {
                for (Class<?> classZ : getScrubbedConvertableClasses(classAParent)) {
                    if (!intermediaries.add(classZ)) {
                        continue;
                    }
                    Function<Z, B> zToB = (Function<Z, B>) findFunction(classZ, classB, intermediaries);
                    if (zToB != null) {
                        Function<A, Z> aToZ = (Function<A, Z>) getFunction(classAParent, classZ);
                        return aToZ.andThen(zToB);
                    }
                    intermediaries.remove(classZ);
                }
            }
        }

        return null;
    }
}
