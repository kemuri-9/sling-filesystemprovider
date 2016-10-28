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

import org.osgi.service.component.annotations.Component;

import net.kemuri9.sling.filesystemprovider.Binary;

/**
 * Implementation of the {@link net.kemuri9.sling.filesystemprovider.PropertyFactory PropertyFactory} interface.
 */
@Component(service = {net.kemuri9.sling.filesystemprovider.PropertyFactory.class})
final class PropertyFactory implements net.kemuri9.sling.filesystemprovider.PropertyFactory {

    @Override
    public Binary createBinaryProperty(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("unable to read from null InputStream");
        }

        return new FileBinary(input);
    }
}
