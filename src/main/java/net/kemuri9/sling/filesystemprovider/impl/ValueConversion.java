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
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;

import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.kemuri9.sling.filesystemprovider.Binary;

@SuppressWarnings("unchecked")
final class ValueConversion {

    /** java time Full ISO 8601 format */
    private static final DateTimeFormatter JT_FULL_ISO_8601 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private static final SimpleDateFormat SDF_FULL_ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /** slf4j logger */
    private static final Logger log = LoggerFactory.getLogger(ValueConversion.class);

    static class JSONStorage {
        /** the value to store in the JSON */
        public final Object value;
        /** the state of the value being a binary data value. */
        public final boolean isBinary;

        public JSONStorage(Object value) {
            this(value, false);
        }

        public JSONStorage(Object value, boolean isBinary) {
            this.value = value;
            this.isBinary = isBinary;
        }
    }

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
     * Convert the provided value into a new target type
     * @param <T> the type of the class
     * @param val the value to convert
     * @param clazz the clazz type to convert into
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
        if (clazz.isArray()) {
            return (T) convertToArray(val, clazz.getComponentType());
        }
        // string conversions
        if (String.class.equals(clazz)) {
            return (T) convertToString(val);
        }
        // number conversions
        if (val instanceof Number && Number.class.isAssignableFrom(clazz)) {
            T numVal = convertToNumber(val, clazz);
            if (numVal != null) {
                return numVal;
            }
        }
        // date conversions
        if (Date.class.isAssignableFrom(clazz)) {
            T dateVal = convertToDate(val, clazz);
            if (dateVal != null) {
                return dateVal;
            }
        }
        // calendar conversions
        if (Calendar.class.isAssignableFrom(clazz)) {
            T calVal = convertToCalendar(val, clazz);
            if (calVal != null) {
                return calVal;
            }
        }
        // time conversions
        if (TemporalAccessor.class.isAssignableFrom(clazz)) {
            T timeVal = convertToTime(val, clazz);
            if (timeVal != null) {
                return timeVal;
            }
        }
        // Binary conversions
        if (val instanceof Binary) {
            Binary bin = (Binary) val;
            // if some number is desired, then use the length of the binary for this purpose
            if (Number.class.isAssignableFrom(clazz)) {
                return convert(bin.getLength(), clazz);
            }
            // if input stream is desired, then get the stream
            if (InputStream.class.equals(clazz)) {
                return (T) Util.getBinaryStreamQuietly(bin);
            }
        }
        if (val instanceof String) {
            // try by string constructor
            try {
                return clazz.getConstructor(String.class).newInstance((String) val);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException | SecurityException e) {
                log.info("Unable to find, access, or invoke String constructor on {}", clazz.getName());
            }
        }
        // deserialize case
        if ((val instanceof InputStream || val instanceof Binary) && Serializable.class.isAssignableFrom(clazz)) {
            log.trace("Attempting deserialization converstion of binary into {}", clazz.getName());

            InputStream binary = (val instanceof InputStream) ? (InputStream) val : Util.getBinaryStreamQuietly((Binary) val);
            Object deserialized = null;
            try (Util.SpecificClassLoadingObjectInputStream objInputStream = new Util.SpecificClassLoadingObjectInputStream(Util.getClassLoader(), binary);) {
                deserialized = objInputStream.readObject();
                objInputStream.close();
                return (T) deserialized;
            } catch (IOException | ClassNotFoundException e) {
                log.error("Error deserializing serializable class data", e);
                return null;
            } catch (ClassCastException e) {
                log.error("deserialized object was not expected type: was {}, expected {}",
                        deserialized.getClass().getName(), clazz.getName());
                return null;
            }
        }
        // serialize case
        if (val instanceof Serializable && (InputStream.class.isAssignableFrom(clazz) || Binary.class.isAssignableFrom(clazz))) {
            log.trace("Attempting serialization on {}", val);
            try {
                @SuppressWarnings("resource")
                FileBinary temporary = new FileBinary();
                ObjectOutputStream output = new ObjectOutputStream(temporary.getOutputStream());
                output.writeObject(val);
                output.close();
                // if the caller really wants InputStream, then do what we can to clean up the leak.
                if (InputStream.class.isAssignableFrom(clazz)) {
                    temporary.setDeleteOnFinalize(true);
                    return (T) temporary.getStream();
                }
            } catch (IOException e) {
                log.error("Error serializing class data", e);
                return null;
            }
        }
        log.warn("Unable to convert {} into {}", val, clazz.getName());
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

    /**
     * Convert the provided value into a {@link Calendar} or derived type.
     * @param <T> the type of the class
     * @param val the value to convert
     * @param clazz the specific {@link Calendar} type to convert into
     * @return the value converted into the specified type, or {@code null} if not possible.
     */
    private static <T> T convertToCalendar(Object val, Class<T> clazz) {
        if (val instanceof String) {
            val = JT_FULL_ISO_8601.parse((String) val, ZonedDateTime::from);
        }
        if (clazz.isAssignableFrom(GregorianCalendar.class)) {
            if (val instanceof ZonedDateTime) {
                return (T) GregorianCalendar.from((ZonedDateTime) val);
            }
            if (val instanceof Date) {
                GregorianCalendar newCal = new GregorianCalendar();
                newCal.setTime((Date) val);
                return (T) newCal;
            }
            if (val instanceof Number) {
                GregorianCalendar newCal = new GregorianCalendar();
                newCal.setTimeInMillis(((Number) val).longValue());
                return (T) newCal;
            }
        }
        return null;
    }

    /**
     * Convert the provided value into a {@link Date} or derived type.
     * @param <T> the type of the class
     * @param val the value to convert
     * @param clazz the specific {@link Date} type to convert into.
     * @return the value converted into the specified type, or {@code null} if not possible.
     */
    private static <T> T convertToDate(Object val, Class<T> clazz) {
        if (java.sql.Timestamp.class.equals(clazz)) {
            if (val instanceof String) {
                return (T) java.sql.Timestamp.valueOf((String) val);
            }
            if (val instanceof Instant) {
                return (T) java.sql.Timestamp.from((Instant) val);
            }
            if (val instanceof Number) {
                return (T) new java.sql.Timestamp(((Number) val).longValue());
            }
        }
        if (java.sql.Time.class.equals(clazz)) {
            if (val instanceof String) {
                return (T) java.sql.Time.valueOf((String) val);
            }
            if (val instanceof LocalTime) {
                return (T) java.sql.Time.valueOf((LocalTime) val);
            }
            if (val instanceof Number) {
                return (T) new java.sql.Time(((Number) val).longValue());
            }
        }
        if (java.sql.Date.class.equals(clazz)) {
            if (val instanceof LocalDate) {
                return (T) java.sql.Date.valueOf((LocalDate) val);
            }
            if (val instanceof String) {
                return (T) java.sql.Date.valueOf((String) val);
            }
            if (val instanceof Number) {
                return (T) new java.sql.Date(((Number) val).longValue());
            }
        }
        if (java.util.Date.class.equals(clazz)) {
            if (val instanceof Calendar) {
                return (T) ((Calendar) val).getTime();
            }
            if (val instanceof Instant) {
                return (T) java.util.Date.from((Instant) val);
            }
            if (val instanceof Number) {
                return (T) new java.util.Date(((Number) val).longValue());
            }
        }
        return null;
    }

    /**
     * Convert the provided value into a {@link Number} or derived type.
     * @param <T> the type of the class
     * @param val the value to convert
     * @param clazz the specific {@link Date} type to convert into.
     * @return the value converted into the specified type, or {@code null} if not possible.
     */
    private static <T> T convertToNumber(Object val, Class<T> clazz) {
        Number numVal = (Number) val;
        if (BigDecimal.class.equals(clazz)) {
            return (T) BigDecimal.valueOf(numVal.doubleValue());
        }
        if (BigInteger.class.equals(clazz)) {
            return (T) BigInteger.valueOf(numVal.longValue());
        }
        if (Byte.class.equals(clazz)) {
            return (T) new Byte(numVal.byteValue());
        }
        if (Double.class.equals(clazz)) {
            return (T) new Double(numVal.doubleValue());
        }
        if (Float.class.equals(clazz)) {
            return (T) new Float(numVal.floatValue());
        }
        if (Integer.class.equals(clazz)) {
            return (T) new Integer(numVal.intValue());
        }
        if (Long.class.equals(clazz)) {
            return (T) new Long(numVal.longValue());
        }
        if (Short.class.equals(clazz)) {
            return (T) new Short(numVal.shortValue());
        }
        return null;
    }

    private static String convertToString(Object val) {
        if (val instanceof GregorianCalendar) {
            val = ((GregorianCalendar) val).toZonedDateTime();
        }
        if (val instanceof Calendar || val instanceof Date) {
            return SDF_FULL_ISO_8601.format(val);
        }
        if (val instanceof ZonedDateTime) {
            return JT_FULL_ISO_8601.format((ZonedDateTime) val);
        }
        return val.toString();
    }

    private static <T> T convertToTime(Object val, Class<T> clazz) {
        if (clazz.isAssignableFrom(ZonedDateTime.class)) {
            if (val instanceof String) {
                return (T) JT_FULL_ISO_8601.parse((String) val, ZonedDateTime::from);
            }
        }
        return null;
    }

    /**
     * Returns an Object of how to store the object in JSON.
     * @param val the value to store in JSON.
     * @return a {@link JSONStorage} object for the types that require the extra meta information, otherwise the value to store.
     */
    static Object convertToJSONStorage(Object val) {
        // nulls to convert to the JSON variation
        if (val == null) {
            return JSONObject.NULL;
        }
        // numbers are as-is
        if (val instanceof Number) {
            return val;
        }
        // strings are as-is
        if (val instanceof String) {
            return val;
        }
        // binaries are stored as their name values with the binary flag.
        if (val instanceof Binary) {
            return new JSONStorage(((Binary) val).getName(), true);
        }
        // the old dates are stored as their milliseconds
        if (val instanceof java.util.Date) {
            return ((java.util.Date) val).getTime();
        }
        if (val instanceof java.util.Calendar) {
            return convertToString(val);
        }
        if (val instanceof TemporalAccessor) {
            return convertToString(val);
        }
        if (val instanceof Serializable) {
            log.warn("serializing {} for storage", val);
            return convertToJSONStorage(convert(val, Binary.class));
        }
        // TODO
        return val.toString();
    }
}
