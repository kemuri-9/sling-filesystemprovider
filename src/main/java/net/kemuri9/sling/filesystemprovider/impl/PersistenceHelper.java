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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.temporal.TemporalAccessor;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.kemuri9.sling.filesystemprovider.Binary;

/**
 * Handles a lot of the persistence mechanisms
 */
final class PersistenceHelper {

    /** small bean class to assist with the json handling of binary types */
    static final class JSONStorage {
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

    /** slf4j logger */
    private static Logger log = LoggerFactory.getLogger(PersistenceHelper.class);

    /** file filter to find the properties file in the resource directory */
    private static DirectoryStream.Filter<Path> DIR_STREAM_FILTER_PROPERTIES = new DirectoryStream.Filter<Path>() {
        @Override
        public boolean accept(Path pathname) throws IOException {
            String name = pathname.getFileName().toString();
            if (!name.startsWith(FSPConstants.FILENAME_PREFIX_FSP + FSPConstants.FILENAME_FRAGMENT_PROPERTIES_FILE)) {
                return false;
            }
            String suffix = name.substring((FSPConstants.FILENAME_PREFIX_FSP + FSPConstants.FILENAME_FRAGMENT_PROPERTIES_FILE).length());
            JSONCompression compression = JSONCompression.fromExtension(suffix);
            return compression != null;
        }
    };

    /** retrieve the compression format from the specified file */
    static JSONCompression compressionFromFile(Path file) {
        String fileName = file.getFileName().toString();
        for (JSONCompression compression : JSONCompression.values()) {
            if (fileName.endsWith(compression.extension)) {
                return compression;
            }
        }
        // default to NONE
        return JSONCompression.NONE;
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
        // the basic number types are as-is
        if (val instanceof Number && val.getClass().getPackage().getName().equals("java.lang")) {
            return val;
        }
        // strings are as-is
        if (val instanceof String) {
            return val;
        }
        // binaries are stored as their name values with the binary flag.
        if (val instanceof FileBinary) {
            return new JSONStorage(((FileBinary) val).getName(), true);
        }
        // user implemented Binary interface - should not actually happen
        if (val instanceof Binary) {
            return null;
        }
        // the old dates are stored as their milliseconds
        if (val instanceof java.util.Date) {
            return ((java.util.Date) val).getTime();
        }
        if (val instanceof java.util.Calendar) {
            return ValueConversion.convert(val, String.class);
        }
        if (val instanceof TemporalAccessor) {
            return ValueConversion.convert(val, String.class);
        }
        if (val instanceof Serializable) {
            return convertToJSONStorage(ValueConversion.convert(val, Binary.class));
        }
        // non serializable, non basic numbers are converted to strings
        if (val instanceof Number) {
            return val.toString();
        }
        // TODO
        return val.toString();
    }

    /**
     * Create a {@link JSONObject} that represents the specified object.
     * @param obj the object to create a JSONObject representation for.
     * @return the JSONObject representation of the object.
     * @throws JSONException if an error occurs on creating the JSON data
     */
    static JSONObject createJSONPropertyObject(Object obj) throws JSONException {
        String type = Object.class.toString();
        boolean isArray = false;
        if (obj != null) {
            if (obj.getClass().isArray()) {
                isArray = true;
                Class<?> elemType = obj.getClass().getComponentType();
                if (elemType.isArray()) {
                    throw new IllegalArgumentException("nested array types are not supported");
                }
                type = elemType.getName();
            } else {
                type = obj.getClass().toString();
            }
        }

        JSONObject jsonObj = new JSONObject();
        jsonObj.put(FSPConstants.JSON_KEY_TYPE, type);
        boolean isBinary = false;
        if (isArray) {
            int arrSize = Array.getLength(obj);
            JSONArray arr = new JSONArray();
            jsonObj.put(FSPConstants.JSON_KEY_VALUES, arr);
            for (int arr_i = 0; arr_i < arrSize; ++arr_i) {
                Object arr_val_i = Array.get(obj, arr_i);
                Object storage = convertToJSONStorage(arr_val_i);
                if (storage instanceof JSONStorage) {
                    JSONStorage jsonStore = (JSONStorage) storage;
                    isBinary |= jsonStore.isBinary;
                    storage = jsonStore.value;
                }
                arr.put(storage);
            }
        } else {
            Object storage = convertToJSONStorage(obj);
            if (storage instanceof JSONStorage) {
                JSONStorage jsonStore = (JSONStorage) storage;
                storage = jsonStore.value;
                isBinary = jsonStore.isBinary;
            }
            jsonObj.put(FSPConstants.JSON_KEY_VALUE, storage);
        }
        if (isBinary) {
            jsonObj.put(FSPConstants.JSON_KEY_BINARY, true);
        }

        return jsonObj;
    }


    /**
     * Retrieve properties for the specified resource
     * @param resource resource to retrieve the properties for
     * @return properties for the resource
     */
    static Map<String, Object> getProperties(FileSystemProviderResource resource) {
        Path propFile = getPropertyFile(resource);
        Map<String, Object> properties = new TreeMap<>();
        if (propFile != null) {

            // otherwise try and read the file accordingly
            JSONCompression compression = compressionFromFile(propFile);
            try (InputStreamReader reader = new InputStreamReader(
                    compression.wrapInput(Files.newInputStream(propFile)), StandardCharsets.UTF_8)) {
                String content = Util.slurp(reader);
                JSONObject jsonProps = new JSONObject(content);

                for (Iterator<String> keyIter = jsonProps.keys(); keyIter.hasNext();) {
                    String key = keyIter.next();
                    JSONObject jsonProp = jsonProps.optJSONObject(key);
                    if (jsonProp == null) {
                        log.warn("json key {} was not an Object", key);
                    }

                    Object value = readJSONPropertyValue(resource.getPath(), jsonProp);
                    properties.put(key, value);
                }

            } catch (FileNotFoundException e) {
                log.error("Property file '{}' disappeared", propFile);
            } catch (IOException e) {
                log.error("Error occurred while reading property file '{}'", propFile, e);
            } catch (JSONException e) {
                log.error("Unable to parse JSON property content in file '{}'", propFile, e);
            }
        }

        return properties;
    }

    private static Path getPropertyFile(FileSystemProviderResource resource) {
        try {
            Iterator<Path> files = Files.newDirectoryStream(resource.getFile(), DIR_STREAM_FILTER_PROPERTIES).iterator();
            return (files.hasNext()) ? files.next() : null;
        } catch (IOException e) {
            log.error("unable to find properties file");
        }
        return null;
    }








    /**
     * Read the binary data indicated by the current value, if applicable.
     * @param path the resource path
     * @param val the value that may be a binary value that requires reading.
     * @param isBinary state of the value being a binary value that requires processing.
     * @return the possibly updated object that underwent binary transformation
     */
    private static Object readBinary(String path, Object val, boolean isBinary) {
        if (!isBinary) {
            // there is no handling to perform, so return as-is
            return val;
        }
        if (!(val instanceof String)) {
            log.error("binary property did not have a string value");
        }

        /* if it's a temporary file, then we need to look for it in the
         * temporary folder, otherwise look in the resource folder */
        String filename = (String) val;
        Path folder = (filename.contains(FSPConstants.FILENAME_FRAGMENT_TEMPORARY))
                ? Util.getTemporaryDirectory() : Paths.get(Util.getAbsPath(path));
        Path file = Paths.get(folder.toAbsolutePath().toString(), filename);

        try {
            return new FileBinary(file);
        } catch (IOException e) {
            log.error("Unable to create Binary representation from {}", file.toAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Create a property value usable in the ValueMap system
     * @param path the resource path (in the repository, not on disk)
     * @param property JSON property to read into data
     * @return java object matching the JSON property data.
     */
    private static Object readJSONPropertyValue(String path, JSONObject property) {

        String type = property.optString(FSPConstants.JSON_KEY_TYPE);
        JSONArray values = property.optJSONArray(FSPConstants.JSON_KEY_VALUES);
        Object value = property.opt(FSPConstants.JSON_KEY_VALUE);
        boolean isBinary = property.optBoolean(FSPConstants.JSON_KEY_BINARY, false);

        Class<?> clazz = Util.loadClass(type);
        if (clazz == null) {
            return null;
        }

        if (values == null) {
            // single value case
            Object valToConvert = readBinary(path, value, isBinary);
            return ValueConversion.convert(valToConvert, clazz);
        } else {
            // multi-value case
            Object vals = Array.newInstance(clazz, values.length());
            for (int valIdx = 0; valIdx < values.length(); ++valIdx) {
                // use opt instead of get to avoid the JSONException
                Object val = readBinary(path, values.opt(valIdx), isBinary);
                Array.set(vals, valIdx, ValueConversion.convert(val, clazz));
            }
            return vals;
        }
    }
}
