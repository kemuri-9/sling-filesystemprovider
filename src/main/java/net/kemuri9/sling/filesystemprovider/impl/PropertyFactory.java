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
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.kemuri9.sling.filesystemprovider.Binary;
import net.kemuri9.sling.filesystemprovider.impl.ValueConversion.JSONStorage;

/**
 * Factory class for creating properties from the JSON objects that represent them
 */
@Component(service = {net.kemuri9.sling.filesystemprovider.PropertyFactory.class})
final class PropertyFactory implements net.kemuri9.sling.filesystemprovider.PropertyFactory {

    /** slf4j logger */
    private static final Logger log = LoggerFactory.getLogger(PropertyFactory.class);

    /**
     * Create a property value usable in the ValueMap system
     * @param path the resource path
     * @param property JSON property to read into data
     * @return java object matching the JSON property data.
     */
    static Object createPropertyValue(String path, JSONObject property) {

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
                Object storage = ValueConversion.convertToJSONStorage(arr_val_i);
                if (storage instanceof JSONStorage) {
                    JSONStorage jsonStore = (JSONStorage) storage;
                    isBinary |= jsonStore.isBinary;
                    storage = jsonStore.value;
                }
                arr.put(storage);
            }
        } else {
            Object storage = ValueConversion.convertToJSONStorage(obj);
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

    @Override
    public Binary createBinaryProperty(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("unable to read from null InputStream");
        }

        return new FileBinary(input);
    }
}
