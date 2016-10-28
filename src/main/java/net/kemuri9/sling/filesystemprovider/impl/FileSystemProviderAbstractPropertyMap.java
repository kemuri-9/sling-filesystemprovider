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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

/**
 * ValueMap functionality shared by both Immutable and Modifiable variants.
 */
abstract class FileSystemProviderAbstractPropertyMap implements ValueMap {

    /** the resource this property map represents */
    protected FileSystemProviderResource resource;

    protected FileSystemProviderAbstractPropertyMap(FileSystemProviderResource resource) {
        this.resource = resource;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("clear is not supported");
    }

    @Override
    public boolean containsKey(Object key) {
        String sKey = getKey(key, true);

        // handle deep get clause
        if (sKey.contains(FSPConstants.RESOURCE_PATH_SEPARATOR)) {
            return handleDeep(sKey, ValueMap::containsKey);
        }
        // otherwise direct get
        return resource.getProperties().containsKey(sKey);
    }

    @Override
    public Object get(Object key) {
        String sKey = getKey(key, true);

        // handle deep get clause
        if (sKey.contains(FSPConstants.RESOURCE_PATH_SEPARATOR)) {
            return handleDeep(sKey, ValueMap::get);
        }
        // otherwise direct get
        return getInternal(sKey);
    }

    /**
     * Retrieve a property value from the JSON backed map, performing necessary operations where applicable.
     * @param key the key to retrieve the property for
     * @return the property value, or {@code null} if the value does not exist
     */
    protected Object getInternal(String key) {
        return resource.getProperties().get(key);
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        Object val = get(name);
        return ValueConversion.convert(val, type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(String name, T defaultValue) {
        if (defaultValue != null) {
            return ValueConversion.convert(get(name), (Class<T>) defaultValue.getClass());
        }
        return (T) get(name);
    }

    @Override
    public boolean containsValue(final Object value) {
        return resource.getProperties().containsValue(value);
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        /* per ValueMap immutability, make the entry set read only in all possible ways.
         * it's also specified that ModifiableValueMaps should not be modified this way. */
        Set<Map.Entry<String, Object>> entries = resource.getProperties().entrySet().stream()
            .map((Map.Entry<String, Object> entry) -> { return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue()); } )
            .collect(Collectors.toSet());
        return Collections.unmodifiableSet(entries);
    }

    /**
     * retrieve the key for the specified name, performing validity checks along the way
     * @param name the name to retrieve the key for.
     * @param pathsAllowed state of paths being allowed (deep read or write)
     * @return key value.
     * @throws IllegalArgumentException<ul>
     * <li>If name is {@code null}.</li>
     * <li>If name is empty.</li>
     * <li>If {@code pathsAllowed} is {@code false} and {@code name} contains a path character</li>
     * </ul>
     */
    protected String getKey(Object name, boolean pathsAllowed) {
        if (name == null) {
            throw new IllegalArgumentException("property name may not be null");
        }
        String key = name.toString();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("property name may not be empty");
        }
        if (!pathsAllowed && key.contains(FSPConstants.RESOURCE_PATH_SEPARATOR)) {
            throw new IllegalArgumentException("property name may not contain '" + FSPConstants.RESOURCE_PATH_SEPARATOR + "'");
        }
        return key;
    }

    /**
     * Handle a deep property read request.
     * @param key the property key that was requested and is deep
     * @param func the function to process on the deep request.
     * @return the result of the deep read request.
     */
    private <R> R handleDeep(String key, BiFunction<ValueMap, String, R> func) {
        int lastPath = key.lastIndexOf(FSPConstants.RESOURCE_PATH_SEPARATOR);
        String relPath = key.substring(0, lastPath);
        String propName = key.substring(lastPath+1);
        Resource child = resource.getChild(relPath);
        if (child == null) {
            return null;
        }
        ValueMap childMap = child.getValueMap();
        return func.apply(childMap, propName);
    }

    @Override
    public boolean isEmpty() {
        return resource.getProperties().isEmpty();
    }

    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(resource.getProperties().keySet());
    }

    @Override
    public int size() {
        return resource.getProperties().size();
    }

    @Override
    public Collection<Object> values() {
        return Collections.unmodifiableCollection(resource.getProperties().values());
    }
}
