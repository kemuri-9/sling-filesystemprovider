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

import java.util.Map;

import org.apache.sling.api.resource.ValueMap;

/**
 * {@link ValueMap} for FileSystemProviderResources.
 * As {@link ValueMap} does not reflect put operations back into the storage like ModifiableValueMap does.
 */
final class FileSystemProviderValueMap extends FileSystemProviderAbstractPropertyMap {

    protected FileSystemProviderValueMap(FileSystemProviderResource resource) {
        super(resource);
    }

    @Override
    public Object put(String key, Object value) {
        throw new UnsupportedOperationException("put is not supported on ValueMap");
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> m) {
        throw new UnsupportedOperationException("putAll is not supported on ValueMap");
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException("remove is not supported on ValueMap");
    }
}
