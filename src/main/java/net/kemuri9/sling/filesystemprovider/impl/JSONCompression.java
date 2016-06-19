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
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Compression formats supported for the JSON property storage
 */
enum JSONCompression {

    /** None */
    NONE(".json"),

    /** GZIP */
    GZIP(".json.gz"),

    /* ZIP is left out due to the fact that it is more overhead compared to GZIP/ZLIB
     * as ZIP is an archive format meant for multiple files.
     * Here things are only a single file so there is no need for multiple file support. */

    /** ZLIB, a.k.a. "Deflate" */
    ZLIB(".json.zlib");

    /** File extension for the format */
    final String extension;


    /**
     * Construct new enumeration value.
     * @param extension preferred extension for the format.
     */
    private JSONCompression(String extension) {
        this.extension = extension;
    }

    public static JSONCompression fromExtension(String extension) {
        for (JSONCompression val : values()) {
            if (val.extension.equals(extension)) {
                return val;
            }
        }
        return null;
    }

    /**
     * Wrap the input stream to include any necessary decompression for the format
     * @param inputStream the input stream to wrap
     * @return the wrapped (decompressed) input stream
     * @throws IOException if the stream can not be processed per the compression format
     */
    public InputStream wrapInput(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }
        switch (this) {
        case GZIP:
            return new GZIPInputStream(inputStream);
        case NONE:
            return inputStream;
        case ZLIB:
            return new InflaterInputStream(inputStream);
        default:
            throw new RuntimeException("A type was missed, fix this now");
        }
    }

    /**
     * Wrap the output stream to include any necessary compression for the format
     * @param outputStream the output stream to wrap
     * @return the wrapped (compressed) output stream
     * @throws IOException if the stream can not be processed per the compression format
     */
    public OutputStream wrapOutput(OutputStream outputStream) throws IOException {
        if (outputStream == null) {
            return null;
        }
        switch (this) {
        case GZIP:
            return new GZIPOutputStream(outputStream);
        case NONE:
            return outputStream;
        case ZLIB:
            return new DeflaterOutputStream(outputStream);
        default:
            throw new RuntimeException("A type was missed, fix this now");
        }
    }
}
