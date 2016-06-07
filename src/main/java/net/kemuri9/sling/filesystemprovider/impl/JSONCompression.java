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

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Compression formats supported for the JSON property storage
 */
enum JSONCompression {

    /** None */
    NONE(null, null, ".json"),

    /** GZIP */
    GZIP(GZIPOutputStream.class, GZIPInputStream.class, ".json.gz"),

    /* ZIP is left out due to the fact that it is more overhead compared to GZIP/ZLIB
     * as ZIP is an archive format meant for multiple files.
     * Here things are only a single file so there is no need for multiple file support. */

    /** ZLIB, a.k.a. "Deflate" */
    ZLIB(DeflaterOutputStream.class, InflaterInputStream.class, ".json.zlib");

    /** File extension for the format */
    final String extension;

    /**
     * Class to utilize in decompressing file contents
     * {@code null} if there is not one.
     */
    final Class<? extends FilterInputStream> inputClass;

    /**
     * Class to utilize in compressing file contents.
     * {@code null} if there is not one.
     */
    final Class<? extends FilterOutputStream> outputClass;

    /**
     * Construct new enumeration value.
     * @param output output stream class that performs the compression.
     * @param input input stream class that performs the decompression.
     * @param extension preferred extension for the format.
     */
    private JSONCompression(Class<? extends FilterOutputStream> output,
            Class<? extends FilterInputStream> input, String extension) {
        this.outputClass = output;
        this.inputClass = input;
        this.extension = extension;
    }
}
