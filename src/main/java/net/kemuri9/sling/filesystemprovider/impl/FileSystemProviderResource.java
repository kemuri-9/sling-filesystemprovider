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
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
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
class FileSystemProviderResource extends AbstractResource {

    /** slf4j logger */
    private static Logger log = LoggerFactory.getLogger(FileSystemProviderResource.class);

    /** file filter to find the properties file in the resource directory */
    private static FileFilter FILEFILTER_PROPERTIES = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            String name = pathname.getName();
            if (!name.startsWith(FSPConstants.FSP_FILENAME_PREFIX + FSPConstants.FSP_PROPERTIES_FILE)) {
                return false;
            }
            String suffix = name.substring((FSPConstants.FSP_FILENAME_PREFIX + FSPConstants.FSP_PROPERTIES_FILE).length());
            JSONCompression compression = JSONCompression.fromExtension(suffix);
            return compression != null;
        }
    };

    /** retrieve the compression format from the specified file */
    static JSONCompression compressionFromFile(File file) {
        String fileName = file.getName();
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
    private File file;

    /** the resource path being represented */
    private String path;

    private ResourceMetadata metadata;

    private JSONObject properties;

    FileSystemProviderResource(Resource parent, FileSystemProvider provider, ResolveContext<FileSystemProviderState> resolveCtx,
            ResourceContext rsrcCtx, File file, String path) {
        if (!file.exists()) {
            throw new IllegalArgumentException("Resources can only be made from existing files");
        }
        if (!file.isDirectory()) {
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

    private File getPropertyFile() {
        File[] files = file.listFiles(FILEFILTER_PROPERTIES);
        return (files != null && files.length == 1) ? files[0] : null;
    }

    private JSONObject getProperties() {
        if (properties != null) {
            return properties;
        }
        File propFile = getPropertyFile();
        if (propFile != null) {
            // otherwise try and read the file accordingly
            JSONCompression compression = compressionFromFile(propFile);
            try (InputStreamReader reader = new InputStreamReader(
                    compression.wrapInput(new FileInputStream(propFile)), StandardCharsets.UTF_8)) {
                String content = IOUtils.toString(reader);
                properties = new JSONObject(content);
            } catch (FileNotFoundException e) {
                log.error("Property file '{}' disappeared", propFile.getAbsolutePath());
            } catch (IOException e) {
                log.error("Error occurred while reading property file '{}'", propFile.getAbsolutePath(), e);
            } catch (JSONException e) {
                log.error("Unable to parse JSON property content in file '{}'", propFile.getAbsoluteFile(), e);
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
        return new FileSystemProviderValueMap(getProperties());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
        if (type == ModifiableValueMap.class) {
            // TODO
            return null;
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
