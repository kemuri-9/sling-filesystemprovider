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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * In memory binary storage. needed for base64 handling
 */
class InMemoryBinary implements Binary {

    private byte[] data;

    InMemoryBinary(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data may not be null");
        }
        this.data = data;
    }

    @Override
    public long getLength() {
        return data.length;
    }

    @Override
    public InputStream getStream() throws IOException {
        return new ByteArrayInputStream(data);
    }
}
