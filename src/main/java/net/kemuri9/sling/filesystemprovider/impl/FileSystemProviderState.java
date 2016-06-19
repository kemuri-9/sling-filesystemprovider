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

import org.apache.sling.api.resource.PersistenceException;

/**
 * State management of the file system provider.
 * Holds transient changes and other state related information
 */
final class FileSystemProviderState implements AutoCloseable {

    /** state of the "session" being live */
    public boolean isLive;

    /** username that the system is authenticated with */
    public String username;

    public FileSystemProviderState() {
        this.isLive = true;
        // default to anonymous
        username = "anonymous";
    }

    @Override
    public void close() {
        isLive = false;
    }

    public void revert() throws PersistenceException {

    }

    public void commit() throws PersistenceException {

    }
}
