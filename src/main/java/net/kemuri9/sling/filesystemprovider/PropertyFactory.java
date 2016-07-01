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
package net.kemuri9.sling.filesystemprovider;

import java.io.IOException;
import java.io.InputStream;

/**
 * Factory for generating particular property values that require special handling to instantiate.
 */
public interface PropertyFactory {

    /**
     * Create a {@link Binary} representing the specified {@link InputStream}.
     * The representation returned will be all the remaining readable data left in the {@link InputStream}.
     *
     * <p>This is not mandatory to be used, as best efforts are implemented to
     * automatically convert {@link InputStream}s into {@link Binary}s where necessary.
     * However, this is recommended to be used to avoid the potential confusion caused by the automatic conversion.</p>
     * @param input the {@link InputStream} to create a {@link Binary} from
     * @return the binary representing the specified {@link InputStream}
     * @throws IOException If an error occurs while reading the {@link InputStream} to create the {@link Binary}
     */
    public Binary createBinaryProperty(InputStream input) throws IOException;
}
