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

import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONObject;

/**
 * {@link ValueMap} for FileSystemProviderResources.
 * As {@link ValueMap} does not reflect put operations back into the storage like ModifiableValueMap does.
 * Here we convert the JSON to a map for in-memory use/life.
 */
class FileSystemProviderValueMap extends HashMap<String, Object> implements ValueMap {

    /** Serialization ID for Serializable standard */
    private static final long serialVersionUID = 444626533489346675L;

    FileSystemProviderValueMap(JSONObject properties) {
        if (properties == null) {
            return;
        }
        // TODO
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        return ValueConversion.convert(this.get(name), type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(String name, T defaultValue) {
        if (defaultValue != null) {
            return ValueConversion.convert(this.get(name), (Class<T>) defaultValue.getClass());
        }
        return (T) get(name);
    }

}
