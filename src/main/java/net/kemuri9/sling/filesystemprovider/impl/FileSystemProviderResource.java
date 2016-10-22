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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import org.apache.sling.api.resource.AbstractResource;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource within the File system Provider system
 */
final class FileSystemProviderResource extends AbstractResource {

    /** slf4j logger */
    private static Logger log = LoggerFactory.getLogger(FileSystemProviderResource.class);

    /** file filter to find the properties file in the resource directory */
    private static DirectoryStream.Filter<Path> DIR_STREAM_FILTER_PROPERTIES = new DirectoryStream.Filter<Path>() {
        @Override
        public boolean accept(Path pathname) throws IOException {
            String name = pathname.getFileName().toString();
            if (!name.startsWith(FSPConstants.FILENAME_PREFIX_FSP + FSPConstants.FILENAME_FRAGMENT_PROPERTIES_FILE)) {
                return false;
            }
            String suffix = name.substring((FSPConstants.FILENAME_PREFIX_FSP + FSPConstants.FILENAME_FRAGMENT_PROPERTIES_FILE).length());
            JSONCompression compression = JSONCompression.fromExtension(suffix);
            return compression != null;
        }
    };

    /** retrieve the compression format from the specified file */
    static JSONCompression compressionFromFile(Path file) {
        String fileName = file.getFileName().toString();
        for (JSONCompression compression : JSONCompression.values()) {
            if (fileName.endsWith(compression.extension)) {
                return compression;
            }
        }
        // default to NONE
        return JSONCompression.NONE;
    }

    /** parent of this resource */
    private Resource parent;

    /** provider that created this resource */
    private FileSystemProvider provider;

    /** state that this provider resource is associated to. */
    private ResolveContext<FileSystemProviderState> context;

    /** the file on disk that this is providing */
    private Path file;

    /** the resource path being represented */
    private String path;

    /** additional metadata held by the resource */
    private ResourceMetadata metadata;

    /** properties for the resource */
    private JSONObject properties;

    FileSystemProviderResource(Resource parent, FileSystemProvider provider, ResolveContext<FileSystemProviderState> resolveCtx,
            ResourceContext rsrcCtx, Path file, String path) {
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Resources can only be made from existing files");
        }
        if (!Files.isDirectory(file)) {
            throw new IllegalArgumentException("Resources can only be made from folders");
        }
        this.parent = parent;
        this.provider = provider;
        this.context = resolveCtx;
        this.file = file;
        this.path = path;
        metadata = new ResourceMetadata();
        metadata.put(ResourceMetadata.RESOLUTION_PATH, path);
    }

    /** {@inheritDoc} */
    @Override
    public Resource getParent() {
        /* TODO: evaluate when parent is null and whether this matches expectations to use
         * it without falling back to AbstractResource's implementation */
        return (parent == null) ? super.getParent() : parent;
    }

    private Path getPropertyFile() {
        try {
            Iterator<Path> files = Files.newDirectoryStream(file, DIR_STREAM_FILTER_PROPERTIES).iterator();
            return (files.hasNext()) ? files.next() : null;
        } catch (IOException e) {
            log.error("unable to find properties file");
        }
        return null;
    }

    /**
     * Retrieve the properties for this resource. These should be considered <strong>READ ONLY</strong>
     * @return the properties for the resource.
     */
    JSONObject getProperties() {
        if (properties != null) {
            return properties;
        }

        // if the current state has (modified) properties for this resource, use those instead of the persisted ones.
        JSONObject props = context.getProviderState().modifiedProperties.get(path);
        if (props != null) {
            properties = props;
            return props;
        }

        Path propFile = getPropertyFile();
        if (propFile != null) {
            // otherwise try and read the file accordingly
            JSONCompression compression = compressionFromFile(propFile);
            try (InputStreamReader reader = new InputStreamReader(
                    compression.wrapInput(Files.newInputStream(propFile)), StandardCharsets.UTF_8)) {
                String content = Util.slurp(reader);
                properties = new JSONObject(content);
            } catch (FileNotFoundException e) {
                log.error("Property file '{}' disappeared", propFile);
            } catch (IOException e) {
                log.error("Error occurred while reading property file '{}'", propFile, e);
            } catch (JSONException e) {
                log.error("Unable to parse JSON property content in file '{}'", propFile, e);
            }
        }

        // if nothing, then default to empty
        if (properties == null) {
            properties = new JSONObject();
        }
        return properties;
    }

    @Override
    public ValueMap getValueMap() {
        return new FileSystemProviderValueMap(this);
    }

    /**
     * Add/Update the specified property on the resource
     * @param propertyName the name of the property that is added/updated
     * @param newProp the data for the new property
     * @throws JSONException if an error occurs adding the new property
     */
    void addProperty(String propertyName, JSONObject newProp) throws JSONException {
        JSONObject properties = getProperties();
        properties.put(propertyName, newProp);
        // flag within the state that this resource has modified properties
        context.getProviderState().modifiedProperties.put(path, properties);
    }

    /**
     * Remove the specified property from the resource
     * @param propertyName the name of the property to remove.
     * @return the value that was removed.
     */
    Object removeProperty(String propertyName) {
        properties = getProperties();
        Object removed = properties.remove(propertyName);
        // flag within the state that this resource has modified properties
        context.getProviderState().modifiedProperties.put(path, properties);
        return removed;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
        if (type == ModifiableValueMap.class) {
            return (AdapterType) new FileSystemProviderModifiableValueMap(this);
        }
        if (type == ValueMap.class) {
            return (AdapterType) getValueMap();
        }

        return super.adaptTo(type);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getResourceType() {
        JSONObject type = getProperties().optJSONObject(FSPConstants.PROPERTY_RESOURCE_TYPE);
        return (type == null) ? null : type.optString(FSPConstants.JSON_KEY_VALUE);
    }

    @Override
    public String getResourceSuperType() {
        JSONObject type = getProperties().optJSONObject(FSPConstants.PROPERTY_RESOURCE_SUPER_TYPE);
        return (type == null) ? null : type.optString(FSPConstants.JSON_KEY_VALUE);
    }

    @Override
    public ResourceMetadata getResourceMetadata() {
        return metadata;
    }

    @Override
    public ResourceResolver getResourceResolver() {
        return context.getResourceResolver();
    }
}
