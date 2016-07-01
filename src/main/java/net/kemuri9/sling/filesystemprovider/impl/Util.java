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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.util.HashMap;

import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.kemuri9.sling.filesystemprovider.Binary;

/**
 * Utilities shared a bit between the individual classes. and lacking a better class name.
 */
class Util {

    /**
     * ObjectInputStream that utilizes the specific class loader
     */
    public static class SpecificClassLoadingObjectInputStream extends ObjectInputStream {

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

    /** Sling ClassLoader Manager for accessing classes within the Sling environment. */
    private static DynamicClassLoaderManager classLoaderManager = null;

    /** configuration */
    private static FileSystemProviderConfig config;

    /** slf4j logger */
    private static Logger log = LoggerFactory.getLogger(Util.class);

    /** mapping of primitive class types to their wrapper types */
    private static final HashMap<Class<?>, Class<?>> primitiveWrapperMap;

    /** Sling Settings */
    private static SlingSettingsService slingSettings;

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
    }

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

    static InputStream getBinaryStreamQuietly(Binary bin) {
       try {
            return bin.getStream();
        } catch (IOException e) {
            log.error("Unable to acquire stream from binary {}", bin, e);
            return null;
        }
    }

    static void init(SlingSettingsService slingSettings, FileSystemProviderConfig config) {
        Util.slingSettings = slingSettings;
        Util.config = config;
    }

    static Class<?> loadClass(ClassLoader cl, String type) {
        if (type == null) {
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

        try {
            return cl.loadClass(type);
        } catch (ClassNotFoundException e) {
            log.error("Unable to load type '{}' used as a property value", type);
            return null;
        }
    }

    static boolean isSpecialPropertyName(String propertyName) {
        return FSPConstants.PROPERTY_RESOURCE_TYPE.equals(propertyName)
                || FSPConstants.PROPERTY_RESOURCE_SUPER_TYPE.equals(propertyName);
    }

    static Class<?> primitiveToWrapper(Class<?> primitiveClass) {
        if (primitiveClass == null || !primitiveClass.isPrimitive()) {
            return null;
        }
        return primitiveWrapperMap.get(primitiveClass);
    }

    static void setDynamicClassLoaderManager(DynamicClassLoaderManager manager) {
        classLoaderManager = manager;
    }

    /**
     * Slurp the contents of an InputStream into the bytes it holds
     * @param input the InputStream to slurp its contents. it will be closed on successful completion.
     * @return the bytes read from the InputStream
     * @throws IOException
     */
    static byte[] slurp(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        input.close();
        return output.toByteArray();
    }

    /**
     * Slurp the contents of a reader into the string it represents.
     * @param reader the reader to slurp its contents. it will be closed on successful completion.
     * @return the read contents of the Reader.
     * @throws IOException If an error occurs reading character data off of the reader
     */
    static String slurp(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[2048];
        int read = 0;
        while ((read = reader.read(buf)) > 0) {
            sb.append(buf, 0, read);
        }
        reader.close();
        return sb.toString();
    }
}
