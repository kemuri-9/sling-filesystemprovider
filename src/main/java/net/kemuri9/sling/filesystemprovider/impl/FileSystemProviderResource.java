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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.apache.sling.api.resource.AbstractResource;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
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
    private Map<String, Object> properties;

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

    /**
     * Retrieve the properties for this resource. These should be considered <strong>READ ONLY</strong>
     * @return the properties for the resource.
     */
    Map<String, Object> getProperties() {
        if (properties != null) {
            return properties;
        }

        // if the current state has (modified) properties for this resource, use those instead of the persisted ones.
        Map<String, Object> props = context.getProviderState().modifiedProperties.get(path);
        // if there are none, then try and read from the persisted data
        if (props == null) {
            props = PersistenceHelper.getProperties(this);
        }

        // if nothing, then default to empty
        if (props == null) {
            props = new TreeMap<>();
        }
        // cache result
        properties = props;
        return properties;
    }

    @Override
    public ValueMap getValueMap() {
        return new FileSystemProviderValueMap(this);
    }

    /**
     * Add/Update the specified property on the resource
     * @param propertyName the name of the property that is added/updated
     * @param newValue the data for the new property
     * @return the old value for the property
     */
    Object addProperty(String propertyName, Object newValue) {
        Map<String, Object> properties = getProperties();
        Object oldValue = properties.put(propertyName, newValue);
        // flag within the state that this resource has modified properties
        context.getProviderState().modifiedProperties.put(path, properties);
        return oldValue;
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
        Object resourceType = getProperties().get(FSPConstants.PROPERTY_RESOURCE_TYPE);
        return (resourceType instanceof String) ? (String) resourceType : null;
    }

    @Override
    public String getResourceSuperType() {
        Object resourceType = getProperties().get(FSPConstants.PROPERTY_RESOURCE_SUPER_TYPE);
        return (resourceType instanceof String) ? (String) resourceType : null;
    }

    @Override
    public ResourceMetadata getResourceMetadata() {
        return metadata;
    }

    @Override
    public ResourceResolver getResourceResolver() {
        return context.getResourceResolver();
    }

    /**
     * Retrieve the path (directory) on disk that is represented by this resource.
     * @return path on disk represented by this resource
     */
    Path getFile() {
        return file;
    }
}
