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

/**
 * Representation of a Binary stream of data
 */
public interface Binary {

    /**
     * Retrieve the length of the binary
     * @return length of the binary stream
     */
    public long getLength();

    /**
     * Retrieve the InputStream for the binary data
     * @return stream of the binary data
     * @throws IOException if an error on acquiring the stream occurs
     */
    public InputStream getStream() throws IOException;
}
