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
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("clear");
    }

    /** slf4j logger */
    private static Logger log = LoggerFactory.getLogger(FileSystemProviderAbstractPropertyMap.class);

    @Override
    public boolean containsKey(Object key) {
        String sKey = getKey(key, true);

        // handle deep get clause
        if (sKey.contains(FSPConstants.RESOURCE_PATH_SEPARATOR)) {
            return handleDeep(sKey, (ValueMap map, String propName) -> { return map.containsKey(propName); });
        }
        // otherwise direct get
        return resource.getProperties().has(sKey);
    }

    @Override
    public Object get(Object key) {
        String sKey = getKey(key, true);

        // handle deep get clause
        if (sKey.contains(FSPConstants.RESOURCE_PATH_SEPARATOR)) {
            return handleDeep(sKey, (ValueMap map, String propName) -> { return map.get(propName); });
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
        JSONObject property = resource.getProperties().optJSONObject(key);
        if (property == null) {
            log.trace("key {} did not represent JSON object, ignoring", key);
            return null;
        }
        return PropertyFactory.createPropertyValue(resource.getPath(), property);
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
        return getJSONKeyStream().map(this::getInternal)
            .filter((Object val) -> { return Objects.equals(value, val); })
            .findFirst().isPresent();
    }

    @Override
    public Set<java.util.Map.Entry<String, Object>> entrySet() {
        return getJSONKeyStream()
            .map((String key) -> { return new AbstractMap.SimpleImmutableEntry<>(key, getInternal(key)); } )
            .collect(Collectors.toSet());
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
        return !resource.getProperties().keys().hasNext();
    }

    @Override
    public Set<String> keySet() {
        return getJSONKeyStream().collect(Collectors.toSet());
    }

    @Override
    public int size() {
        return (int) getJSONKeyStream().count();
    }

    @Override
    public Collection<Object> values() {
        return getJSONKeyStream().map(this::getInternal).collect(Collectors.toList());
    }

    /**
     * Generate a {@link Stream} of the {@link String}s representing property names
     * @return Stream of property names
     */
    private Stream<String> getJSONKeyStream() {
        Builder<String> builder = Stream.builder();
        final JSONObject props = resource.getProperties();
        props.keys().forEachRemaining(builder);
        return builder.build();
    }
}
