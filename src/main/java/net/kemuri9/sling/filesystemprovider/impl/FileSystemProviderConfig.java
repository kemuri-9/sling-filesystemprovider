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

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "File System ResourceProvider",
        description = "The configuration for the File System ResourceProvider")
public @interface FileSystemProviderConfig {

    @AttributeDefinition(name = "Root paths",
            description = "Root paths that this provider is mounted at to start providing for")
    String[] provider_root() default {"/"};

    @AttributeDefinition(name = "File Repository Root",
            description = "Root path under the sling home directory that stores the contents of the file repository")
    String repository_root() default "filerepository";

    @AttributeDefinition(name = "Compress JSON Property storage",
            description = "State of Compressing the JSON file that manages properties of Resources")
    JSONCompression json_property_compression() default JSONCompression.NONE;

    @AttributeDefinition(name = "Pretty print JSON Property storage",
            description = "State of pretty printing the JSON file that manages properties of Resources")
    boolean json_property_pretty_print() default false;

    @AttributeDefinition(name = "Store JSON Binary Properties as Files",
            description = "State of storing binary (e.g. InputStream) properties as separate files instead of within the JSON properties file")
    boolean json_property_binaries_as_files() default true;
}
