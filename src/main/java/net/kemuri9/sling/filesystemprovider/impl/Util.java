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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;

import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.kemuri9.sling.filesystemprovider.Binary;

/**
 * Utilities shared a bit between the individual classes. and lacking a better class name.
 */
final class Util {

    /**
     * {@link FileVisitor} that deletes everything it sees, including where it starts navigating from
     */
    static final class DeletingFileVisitor implements FileVisitor<Path> {

        /** state of logging what was deleting */
        private boolean logVisitations;

        DeletingFileVisitor(boolean logVisits) {
            logVisitations = logVisits;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (logVisitations) {
                log.warn("found left over file {}", file);
            }
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (logVisitations) {
                log.debug("deleting directory {}", dir);
            }
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * {@link ObjectInputStream} that utilizes the specific class loader.
     */
    static final class SpecificClassLoadingObjectInputStream extends ObjectInputStream {

        private ClassLoader classLoader;

        public SpecificClassLoadingObjectInputStream(ClassLoader cl, InputStream in) throws IOException {
            super(in);
            this.classLoader = cl;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass objectStreamClass)
                throws IOException, ClassNotFoundException {

            Class<?> clazz = Class.forName(objectStreamClass.getName(), false, classLoader);

            if (clazz != null) {
                // the classloader knows of the class
                return clazz;
            } else {
                // classloader knows not of class, let the super classloader do it
                return super.resolveClass(objectStreamClass);
            }
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
            Class<?>[] interfaceClasses = new Class[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) {
                interfaceClasses[i] = Class.forName(interfaces[i], false, classLoader);
            }
            try {
                return Proxy.getProxyClass(classLoader, interfaceClasses);
            } catch (IllegalArgumentException e) {
                return super.resolveProxyClass(interfaces);
            }
        }
    }

    /**
     * {@link OutputStream} meant for piping directly to an {@link InputStream} in memory,
     * trying to minimize memory copies.
     */
    static final class CopyStream extends ByteArrayOutputStream {
        /**
         * Create a new CopyStream with the provided initial byte size
         * @param size
         */
        public CopyStream(int size) {
            super(size);
        }

        /**
         * Retrieve an {@link InputStream} from data currently in this stream.
         * @return {@link InputStream} of data currently in this stream
         */
        public InputStream toInputStream() {
            return new ByteArrayInputStream(this.buf, 0, this.count);
        }
    }

    /** Sling ClassLoader Manager for accessing classes within the Sling environment. */
    private static volatile DynamicClassLoaderManager classLoaderManager = null;

    /** Comparator for comparing the Class type */
    static final Comparator<Class<?>> COMPARATOR_CLASS = new Comparator<Class<?>>() {

        @Override
        public int compare(Class<?> o1, Class<?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

    /** configuration */
    private static FileSystemProviderConfig config;

    /** File walker that deletes what it comes across */
    private static FileVisitor<Path> FILE_VISITOR_DELETING = new DeletingFileVisitor(true);

    /** slf4j logger */
    private static Logger log = LoggerFactory.getLogger(Util.class);

    /** mapping of primitive class types to their wrapper types */
    private static final HashMap<Class<?>, Class<?>> primitiveWrapperMap;

    /** Sling Settings */
    private static SlingSettingsService slingSettings;

    /** Temporary Directory */
    private static final Path tempDir;

    static {
        primitiveWrapperMap = new HashMap<>();
        primitiveWrapperMap.put(Boolean.TYPE, Boolean.class);
        primitiveWrapperMap.put(Byte.TYPE, Byte.class);
        primitiveWrapperMap.put(Character.TYPE, Character.class);
        primitiveWrapperMap.put(Short.TYPE, Short.class);
        primitiveWrapperMap.put(Integer.TYPE, Integer.class);
        primitiveWrapperMap.put(Long.TYPE, Long.class);
        primitiveWrapperMap.put(Double.TYPE, Double.class);
        primitiveWrapperMap.put(Float.TYPE, Float.class);
        primitiveWrapperMap.put(Void.TYPE, Void.TYPE);

        Path dir = null;
        try {
            dir = Files.createTempDirectory("sling-filesystemprovider");
        } catch (IOException e) {
            log.error("unable to create temporary directory");
        }
        if (dir == null) {
            try {
                Path tempFile = Files.createTempFile("test-creation", null);
                dir = tempFile.getParent();
                Files.deleteIfExists(tempFile);
            }
            catch (IOException e) {
                log.error("unable to create temporary file");
            }
        }
        tempDir = dir;
    }

    /**
     * Copy data from the {@code InputStream} to the {@code OutputStream}.
     * Both streams should be closed by the caller.
     * @param input the source of data to copy from
     * @param output the target for the data to copy into.
     * @throws IOException if an error occurs on reading the data.
     */
    static void copy(InputStream input, OutputStream output) throws IOException {
        if (input == null) {
            throw new IOException("can not copy from null InputStream");
        }
        if (output == null) {
            throw new IOException("can not copy into null OutputStream");
        }
        byte[] buf = new byte[4096];
        int read = 0;
        while ((read = input.read(buf, 0, buf.length)) > 0) {
            output.write(buf, 0, read);
        }
    }

    /**
     * Perform one-time uninitialization routines
     */
    static void destroy() {
        try {
            Files.walkFileTree(tempDir, FILE_VISITOR_DELETING);
        } catch (IOException e) {
            log.error("failed to clean up temporary files", e);
        }
    }

    /**
     * Retrieve the absolute path on the file system for the specified resource
     * @param rsrcPath the resource path to retrieve its corresponding file system path
     * @return file system path representing the resource path
     */
    static String getAbsPath(String rsrcPath) {
        if (rsrcPath == null) {
            return null;
        }
        // no funny business in giving a relative path
        if (!rsrcPath.startsWith("/")) {
            rsrcPath = "/" + rsrcPath;
        }
        return slingSettings.getAbsolutePathWithinSlingHome(config.repository_root() + rsrcPath);
    }

    /**
     * Retrieve a class loader to load classes with.
     * @return class loader
     */
    static ClassLoader getClassLoader() {
        ClassLoader dynamicCl = getDynamicClassLoader();
        return (dynamicCl == null) ? Thread.currentThread().getContextClassLoader() : dynamicCl;
    }

    /**
     * Retrieve the Sling Dynamic Class Loader, if it is available.
     * @return Sling Dynamic Class Loader. {@code null} if not available.
     */
    static ClassLoader getDynamicClassLoader() {
        return (classLoaderManager == null) ? null : classLoaderManager.getDynamicClassLoader();
    }

    /** Retrieve the directory in which temporary files should be managed */
    static Path getTemporaryDirectory() {
        return tempDir;
    }

    static InputStream getBinaryStreamQuietly(Binary bin) {
        if (bin == null) {
            log.error("provided bin is null");
            return null;
        }
        try {
            return bin.getStream();
        } catch (IOException e) {
            log.error("Unable to acquire stream from binary {}", bin, e);
            return null;
        }
    }

    /**
     * Perform one-time initialization routines
     * @param slingSettings the sling settings to initialize with
     * @param config the configuration to initialize with
     */
    static void init(SlingSettingsService slingSettings, FileSystemProviderConfig config) {
        Util.slingSettings = slingSettings;
        Util.config = config;
    }

    static Class<?> loadClass(String type) {
        return loadClass(Util.getClassLoader(), type);
    }

    /**
     * Load the class representing the indicated type
     * @param cl the {@link ClassLoader} to utilize in loading the class
     * @param type the string typename to load
     * @return the loaded class, or {@code null} if not available to be loaded
     */
    static Class<?> loadClass(ClassLoader cl, String type) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        switch (type) {
        // primitives
        case "boolean":
            return Boolean.TYPE;
        case "byte":
            return Byte.TYPE;
        case "char":
            return Character.TYPE;
        case "double":
            return Double.TYPE;
        case "float":
            return Float.TYPE;
        case "int":
            return Integer.TYPE;
        case "long":
            return Long.TYPE;
        case "short":
            return Short.TYPE;
        // this shouldn't show up, but just in case...
        case "void":
            return Void.TYPE;

        // simple array primitives
        case "[B":
            return byte[].class;
        case "[C":
            return char[].class;
        case "[D":
            return double[].class;
        case "[F":
            return float[].class;
        case "[I":
            return int[].class;
        case "[J":
            return long[].class;
        case "[S":
            return short[].class;
        case "[Z":
            return boolean[].class;
        }

        if (cl == null) {
            log.warn("provided ClassLoader was null, defaulting to thread's classloader");
            cl = Thread.currentThread().getContextClassLoader();
        }

        try {
            return cl.loadClass(type);
        } catch (ClassNotFoundException e) {
            log.error("Unable to load type '{}' used as a property value", type);
            return null;
        }
    }

    /**
     * Retrieve the state of the indicated propertyName being a specially handled property name.
     * @param propertyName name of the property to check being a special-class name.
     * @return state of the propertyName being special.
     */
    static boolean isSpecialPropertyName(String propertyName) {
        return FSPConstants.PROPERTY_RESOURCE_TYPE.equals(propertyName)
                || FSPConstants.PROPERTY_RESOURCE_SUPER_TYPE.equals(propertyName);
    }

    /**
     * Retrieve the wrapper class for the indicated primitive class
     * @param primitiveClass the primitive class to retrieve its corresponding wrapper class
     * @return the wrapper class for the indicated primitive class, {@code null} if not applicable.
     */
    static Class<?> primitiveToWrapper(Class<?> primitiveClass) {
        if (primitiveClass == null || !primitiveClass.isPrimitive()) {
            return null;
        }
        return primitiveWrapperMap.get(primitiveClass);
    }

    /**
     * Set the {@link DynamicClassLoaderManager} to use in loading classes from the sling environment.
     * @param manager the instance of DynamicClassLoaderManager to utilize.
     */
    static void setDynamicClassLoaderManager(DynamicClassLoaderManager manager) {
        classLoaderManager = manager;
    }

    /**
     * Slurp the contents of an {@link InputStream} into the bytes it holds.
     * The provided InputStream instance is closed on successful completion.
     * @param input the InputStream to slurp its contents. it will be closed on successful completion.
     * @return the bytes read from the InputStream.
     * @throws IOException If an error occurs reading the binary data from the InputStream.
     */
    static byte[] slurp(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        input.close();
        return output.toByteArray();
    }

    /**
     * Slurp the contents of a {@link Reader} into the string it represents.
     * The provided Reader instance is closed on successful completion.
     * @param reader the reader to slurp its contents. it will be closed on successful completion.
     * @return the read contents of the Reader.
     * @throws IOException If an error occurs reading character data off of the reader
     */
    static String slurp(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder(2048);
        char[] buf = new char[2048];
        int read = 0;
        while ((read = reader.read(buf)) > 0) {
            sb.append(buf, 0, read);
        }
        reader.close();
        return sb.toString();
    }
}
