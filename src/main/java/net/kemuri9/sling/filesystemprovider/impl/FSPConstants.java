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

/**
 * Constants for internal use of the implementation
 */
final class FSPConstants {

    /** Filename extension indicating binary content */
    public static final String FSP_EXTENSION_BINARY = ".bin";

    /**
     * Prefix that is on all internal files managed by the FSP system.
     * Such files should not be returned nor listed as possible resources.
     */
    public static final String FSP_FILENAME_PREFIX = "_sling_fsp_";

    /** Filename fragment indicating the properties of a resource */
    public static final String FSP_PROPERTIES_FILE = "properties";

    /** JSON Property key indicating the state of utilizing external (to the JSON) storage */
    public static final String JSON_KEY_EXTERNAL = "external";

    /** JSON Property key indicating the type of property */
    public static final String JSON_KEY_TYPE = "type";

    /** JSON Property key indicating the singular value of the property */
    public static final String JSON_KEY_VALUE = "value";

    /** JSON Property key indicating the multiple values of the property */
    public static final String JSON_KEY_VALUES = "values";

    /** Property name indicating the resource's syper type */
    public static final String PROPERTY_RESOURCE_SUPER_TYPE = "sling:resourceSuperType";

    /** Property name indicating the resource's type */
    public static final String PROPERTY_RESOURCE_TYPE = "sling:resourceType";
}
