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

import java.util.HashMap;
import java.util.Iterator;

import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ValueMap} for FileSystemProviderResources.
 * As {@link ValueMap} does not reflect put operations back into the storage like ModifiableValueMap does.
 * Here we convert the JSON to a map for in-memory use/life.
 */
final class FileSystemProviderValueMap extends HashMap<String, Object> implements ValueMap {

    /** slf4j logger */
    private static Logger log = LoggerFactory.getLogger(FileSystemProviderValueMap.class);

    /** Serialization ID for Serializable standard */
    private static final long serialVersionUID = 444626533489346675L;

    FileSystemProviderValueMap(String resourcePath, JSONObject properties) {
        if (properties == null) {
            return;
        }

        for (Iterator<String> keyIter = properties.keys(); keyIter.hasNext();) {
            String key = keyIter.next();
            if (Util.isSpecialPropertyName(key)) {
                // skip special property names
                continue;
            }
            JSONObject property = properties.optJSONObject(key);
            if (property == null) {
                log.error("key {} did not represent JSON object, ignoring", key);
                continue;
            }
            Object value = PropertyFactory.createPropertyValue(resourcePath, property);
            this.put(key, value);
        }
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        return ValueConversion.convert(get(name), type);
    }

    @Override
    public Object put(String name, Object value) {
//        // TODO: evaluate if this is beneficial here or not since this is not a ModifiableValueMap
//        if (value instanceof InputStream) {
//            InputStream input = (InputStream) value;
//            try {
//                value = new FileBinary(input);
//            } catch (IOException e) {
//                log.error("error occurred while converting InputStream into Binary", e);
//            }
//        }
        return super.put(name, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(String name, T defaultValue) {
        if (defaultValue != null) {
            return ValueConversion.convert(get(name), (Class<T>) defaultValue.getClass());
        }
        return (T) get(name);
    }

}
