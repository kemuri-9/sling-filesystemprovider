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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.kemuri9.sling.filesystemprovider.Binary;

/**
 * Binary representation that is a file on disk
 */
final class FileBinary implements Binary {

    /** slf4j logger */
    private static Logger log = LoggerFactory.getLogger(FileBinary.class);

    /** File representing the binary */
    private Path file;

    /** state of the file being temporary */
    private boolean isTemporary;

    /**
     * Create a new temporary file for storage that is yet to be written in.
     * @throws IOException when an IO Error occurs trying to create the temporary file.
     */
    FileBinary() throws IOException {
        file = Files.createTempFile(Util.getTemporaryDirectory(), FSPConstants.FILENAME_PREFIX_FSP, FSPConstants.FILENAME_EXTENSION_BINARY);
        isTemporary = true;
    }

    /**
     * Create a representation of a pre-existing file on disk
     * @param file the pre-existing file on disk to represent as a binary
     * @throws IOException When the provided {@link Path} does not exist or is not a file.
     */
    FileBinary(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("Unable to create File binary from non-existing file");
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("Unable to create File binary from non file");
        }
        this.file = file;
        isTemporary = file.getFileName().toString().contains(FSPConstants.FILENAME_FRAGMENT_TEMPORARY);
    }

    /**
     * Create a representation of an {@link InputStream}. This will be considered temporary storage
     * @param input the {@link InputStream} to create a representation from
     * @throws IOException When an IO Error occurs trying to read the input stream or write the data to the temporary storage
     */
    FileBinary(InputStream input) throws IOException {
        file = Files.createTempFile(Util.getTemporaryDirectory(), FSPConstants.FILENAME_PREFIX_FSP, FSPConstants.FILENAME_EXTENSION_BINARY);
        try (OutputStream fileOutput = Files.newOutputStream(file)) {
            Util.copy(input, fileOutput);
        }
        input.close();
        isTemporary = true;
    }

    @Override
    public void close() {
        dispose();
    }

    @Override
    public void dispose() {
        // if the file is temporary then try to delete it from disk
        if (isTemporary && Files.exists(file)) {
            boolean deleted = false;
            try {
                deleted = Files.deleteIfExists(file);
            } catch (IOException e) {
                log.error("error occurred deleting {}", file, e);
            }
            if (!deleted) {
                log.error("unable to delete {}, trying again on JVM shutdown", file);
                // and if it can't be deleted now, then try again later
                file.toFile().deleteOnExit();
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FileBinary)) {
            return false;
        }

        FileBinary fileBin = (FileBinary) obj;
        try {
            return Files.isSameFile(file, fileBin.file);
        } catch (IOException | SecurityException e) {
            // fall back to path checking
            return file.toAbsolutePath().toString().equals(fileBin.file.toAbsolutePath().toString());
        }
    }

    @Override
    public long getLength() {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public String getName() {
        return file.getFileName().toString();
    }

    /**
     * Retrieve an {@link OutputStream} for writing into the binary content. this should be used
     * <strong>VERY CAREFULLY</strong> as it will overwrite the existing data.
     * @return OutputStream for writing to the file
     * @throws IOException
     */
    public OutputStream getOutputStream() throws IOException {
        return Files.newOutputStream(file, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }

    @Override
    public InputStream getStream() throws IOException {
        return Files.newInputStream(file);
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

    /**
     * Move this file binary to a new location
     * @param newLocation the new location of the binary
     * @throws IOException if the move operation fails.
     */
    public void move(Path newLocation) throws IOException {
        Files.move(file, newLocation);
    }

    /**
     * Set the state of this FileBinary representing temporary binary storage.
     * @param isTemporary newState of being temporary
     */
    public void setTemporary(boolean isTemporary) {
        this.isTemporary = isTemporary;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(getClass().getName())
                .append(" name=").append(getName()).toString();
    }
}
