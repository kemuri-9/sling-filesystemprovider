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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import net.kemuri9.sling.filesystemprovider.Binary;

/**
 * Binary representation that is a file on disk
 */
final class FileBinary implements Binary {

    /** File representing the binary */
    private File file;

    /** state of the file being temporary */
    private boolean isTemporary;

    /**
     * Create a representation of a pre-existing file on disk
     * @param file the pre-existing file on disk to represent as a binary
     * @throws IOException When the provided {@link File} does not exist or is not a file.
     */
    FileBinary(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("Unable to create File binary from non-existing file");
        }
        if (!file.isFile()) {
            throw new IOException("Unable to create File binary from non file");
        }
        this.file = file;
        isTemporary = false;
    }

    /**
     * Create a representation of an {@link InputStream}. This will be considered temporary storage
     * @param input the {@link InputStream} to create a representation from
     * @throws IOException When an IO Error occurs trying to read the input stream or write the data to the temporary storage
     */
    FileBinary(InputStream input) throws IOException {
        file = File.createTempFile(FSPConstants.FILENAME_PREFIX_FSP, "temporary-stream.bin");
        FileOutputStream fileOutput = new FileOutputStream(file);
        Util.copy(input, fileOutput);
        input.close();
        isTemporary = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FileBinary)) {
            return false;
        }

        FileBinary fileBin = (FileBinary) obj;
        return file.equals(fileBin.file);
    }

    @Override
    public long getLength() {
        return file.length();
    }

    @Override
    public InputStream getStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public void finalize() {
        // if the file is temporary then try to delete it from disk
        if (isTemporary && !file.delete()) {
            // and if it can't be deleted now, then try again later
            file.deleteOnExit();
        }
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    /**
     * Retrieve the state of this FileBinary representing temporary binary storage.
     * @return state of this FileBinary representing temporary binary storage.
     */
    public boolean isTemporary() {
        return isTemporary;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(getClass().getName())
                .append(" path=").append(file.getAbsolutePath()).toString();
    }
}
