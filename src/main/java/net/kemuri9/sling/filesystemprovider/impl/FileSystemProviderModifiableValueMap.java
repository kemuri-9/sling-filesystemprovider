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
import java.util.Map;

import org.apache.sling.api.resource.ModifiableValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link ModifiableValueMap} for updating/storing properties
 */
final class FileSystemProviderModifiableValueMap extends FileSystemProviderAbstractPropertyMap
    implements ModifiableValueMap {

    /** slf4j logger */
    private static Logger log = LoggerFactory.getLogger(FileSystemProviderAbstractPropertyMap.class);

    protected FileSystemProviderModifiableValueMap(FileSystemProviderResource resource) {
        super(resource);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> m) {
        if (m == null || m.isEmpty()) {
            return;
        }
        m.entrySet().forEach(this::put);
    }

    private void put(Map.Entry<? extends String, ? extends Object> entry) {
        put(entry.getKey(), entry.getValue());
    }

    @Override
    public Object put(String key, Object value) {
        key = getKey(key, false);
        if (value instanceof InputStream) {
            InputStream input = (InputStream) value;
            try {
                value = new FileBinary(input);
            } catch (IOException e) {
                log.error("error occurred while converting InputStream into Binary", e);
            }
        }

        return resource.addProperty(key, value);
    }

    @Override
    public Object remove(Object key) {
        String sKey = getKey(key, false);
        return resource.removeProperty(sKey);
    }
}
