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
import java.util.TreeMap;

import org.apache.sling.api.resource.PersistenceException;

/**
 * State management of the file system provider.
 * Holds transient changes and other state related information
 */
final class FileSystemProviderState implements AutoCloseable {

    /**
     * Retrieve a time usable for tracking modifications.
     * @return time in milliseconds
     */
    private static long getTime() {
        return System.currentTimeMillis();
    }

    /** state of the "session" being live */
    public boolean isLive;

    /** the point in time the 'state' is in. in milliseconds */
    public long stateTime;

    /** username that the system is authenticated with */
    public String username;

    /** map of resource paths to their current (modified) properties */
    public TreeMap<String, Map<String, Object>> modifiedProperties;

    public FileSystemProviderState() {
        this.isLive = true;
        // default to anonymous
        username = "anonymous";
        stateTime = getTime();
        modifiedProperties = new TreeMap<>();
    }

    @Override
    public void close() {
        isLive = false;
        modifiedProperties.clear();
    }

    /**
     * Refresh the current session to newer updated data in the system
     */
    void refresh() {
        stateTime = getTime();
    }

    /**
     * Retrieve the state of the current provider state being modified.
     * @return state of the provider being modified.
     */
    boolean isModified() {
        return !modifiedProperties.isEmpty();
    }

    /**
     * Revert changes to the current provider state
     */
    void revert() {
        modifiedProperties.clear();
    }

    /**
     * commit the pending changes to the file system
     * @throws PersistenceException
     */
    void commit() throws PersistenceException {
        // TODO
        throw new UnsupportedOperationException("not yet");
    }
}
