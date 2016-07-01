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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.kemuri9.sling.filesystemprovider.Binary;

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
    public static Object createPropertyValue(String path, JSONObject property) {

        String type = property.optString(FSPConstants.JSON_KEY_TYPE);
        JSONArray values = property.optJSONArray(FSPConstants.JSON_KEY_VALUES);
        Object value = property.opt(FSPConstants.JSON_KEY_VALUE);
        boolean isBinary = property.optBoolean(FSPConstants.JSON_KEY_BINARY, false);

        Class<?> clazz = Util.loadClass(Util.getClassLoader(), type);
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

    private static Object readBinary(String path, Object val, boolean isBinary) {
        if (!isBinary) {
            // there is no handling to perform, so return as-is
            return val;
        }
        if (!(val instanceof String)) {
            log.error("binary property did not have a string value");
        }

        String filename = (String) val;
        File file = new File(Util.getAbsPath(path), filename);

        try {
            return new FileBinary(file);
        } catch (IOException e) {
            log.error("Unable to create Binary representation from {}", file.getAbsolutePath(), e);
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
