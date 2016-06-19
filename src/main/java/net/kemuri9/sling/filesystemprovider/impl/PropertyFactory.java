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
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;

import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating properties from the JSON objects that represent them
 */
class PropertyFactory {

    static class CustomLoaderObjectInputStream extends ObjectInputStream {
        private ClassLoader classLoader;

        public CustomLoaderObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
            super(in);
            this.classLoader = classLoader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            if (this.classLoader != null) {
                try {
                    return Class.forName(name, false, this.classLoader);
                } catch (ClassNotFoundException ex) {
                    log.error("Could not load class {} with custom loader", name, ex);
                }
            }
            return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        }
    }

    /** slf4j logger */
    private static final Logger log = LoggerFactory.getLogger(PropertyFactory.class);

    /**
     * Create a property usable in the ValueMap system
     * @param property JSON property to read into data
     * @return java object matching the JSON property data.
     */
    public static Object createProperty(DynamicClassLoaderManager manager, JSONObject property) {

        String type = property.optString(FSPConstants.JSON_KEY_TYPE);
        JSONArray values = property.optJSONArray(FSPConstants.JSON_KEY_VALUES);
        Object value = property.opt(FSPConstants.JSON_KEY_VALUE);
        boolean external = property.optBoolean(FSPConstants.JSON_KEY_EXTERNAL, false);

        ClassLoader dynamicCl = (manager == null) ? null : manager.getDynamicClassLoader();
        ClassLoader cl = (dynamicCl == null) ? Thread.currentThread().getContextClassLoader() : dynamicCl;
        Class<?> clazz = null;
        try {
            clazz = cl.loadClass(type);
        } catch (ClassNotFoundException e) {
            log.error("Unable to load type '{}' used as a property value", type);
            return null;
        }

        switch (type) {
            case "java.lang.Integer":

            case "java.lang.String":
                break;
            default:

                if (!Serializable.class.isAssignableFrom(clazz)) {
                    log.error("Type '{}' is not Serializable, can not be read", type);
                    return null;
                }

                break;
        }

        return null;
    }


}
