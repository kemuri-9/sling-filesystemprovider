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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unchecked")
class ValueConversion {

    /** slf4j logger */
    private static final Logger log = LoggerFactory.getLogger(ValueConversion.class);

    static <T> T[] convertToArray(Object val, Class<T> elemType) {
        if (elemType == null) {
            throw new IllegalArgumentException("Unable to convert to array of null type");
        }
        if (elemType.isArray()) {
            throw new IllegalArgumentException("Unable to convert to multidimensional array");
        }
        if (val == null) {
            return null;
        }
        if (val instanceof JSONArray) {
            JSONArray vals = (JSONArray) val;
            int length = vals.length();
            T[] array = (T[]) Array.newInstance(elemType, length);
            for (int valI = 0; valI < length; ++valI) {
                Object subVal = null;
                try {
                    subVal = vals.get(valI);
                } catch (JSONException e) {
                    log.error("Unable to get value at index {}", valI);
                }

                Array.set(array, valI, convert(subVal, elemType));
            }
            return array;
        }
        if (val instanceof Collection) {
            Collection<?> col = (Collection<?>) val;
            T[] array = col.stream().map(subVal -> convertToArray(subVal, elemType))
                    .toArray(size -> (T[]) Array.newInstance(elemType, size));
            return array;
        }
        if (val instanceof Iterable) {
            return convertToArray(((Iterable<?>) val).iterator(), elemType);
        }
        if (val instanceof Iterator) {
            Iterator<?> iter = (Iterator<?>) val;
            ArrayList<Object> buildup = new ArrayList<>(32); // guess
            // not much benefit to the forEachRemaining lambda here, so don't bother.
            while (iter.hasNext()) {
                Object subVal = convert(iter.next(), elemType);
                buildup.add(subVal);
            }
            T[] array = (T[]) Array.newInstance(elemType, buildup.size());
            buildup.toArray(array);
            return array;
        }
        // single value conversion
        T[] array = (T[]) Array.newInstance(elemType, 1);
        Array.set(array, 0, convert(val, elemType));
        return array;
    }

    static <T> T convert(Object val, Class<T> clazz) {
        // if null, then no conversion necessary
        if (val == null) {
            return null;
        }
        // if already the target, then no change necessary, neat
        if (clazz.isInstance(val)) {
            return clazz.cast(val);
        }
        if (clazz.isArray()) {
            return (T) convertToArray(val, clazz.getComponentType());
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
        log.warn("Unable to convert {} into {}", val, clazz.getName());
        return null;
    }

}
