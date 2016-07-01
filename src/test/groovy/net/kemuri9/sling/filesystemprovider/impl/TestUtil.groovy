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
package net.kemuri9.sling.filesystemprovider.impl

import java.lang.annotation.Annotation

class TestUtil {

    static FileSystemProviderConfig newConfig() {
        return newConfig('.', ['/'] as String[], JSONCompression.NONE, false)
    }

    static FileSystemProviderConfig newConfig(final String rootPath, final String[] providerRoots,
            final JSONCompression jsonCompression, final boolean jsonPrettyPrint) {
        new FileSystemProviderConfig() {

            @Override
            public String repository_root() {
                return rootPath
            }

            @Override
            public String[] provider_root() {
                return providerRoots
            }

            @Override
            public JSONCompression json_property_compression() {
                return jsonCompression
            }

            @Override
            boolean json_property_pretty_print() {
                return false
            }

            @Override
            Class<? extends Annotation> annotationType() {
                return FileSystemProviderConfig.class;
            }
        }
    }

}