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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.settings.SlingSettingsService;
import org.apache.sling.spi.resource.provider.ProviderContext;
import org.apache.sling.spi.resource.provider.QueryLanguageProvider;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = {ResourceProvider.class},
    immediate = true,
    property = {
            ResourceProvider.PROPERTY_NAME + "=File System",
            // modification is to be supported
            ResourceProvider.PROPERTY_MODIFIABLE + ":Boolean=true",
            // adaptTo is to be supported
            ResourceProvider.PROPERTY_ADAPTABLE + ":Boolean=true",
            /* say authentication is required to receive authenticate and logout calls
             * as these are the gate keepers of state management.
             * though authentication is not actually implemented yet. */
            ResourceProvider.PROPERTY_AUTHENTICATE + "=" + ResourceProvider.AUTHENTICATE_REQUIRED,
            // attributes are to be supported
            ResourceProvider.PROPERTY_ATTRIBUTABLE + ":Boolean=true",
            // refresh is to be supported
            ResourceProvider.PROPERTY_REFRESHABLE + ":Boolean=true",
            // for now do not use any system provided resource access security
            ResourceProvider.PROPERTY_USE_RESOURCE_ACCESS_SECURITY + ":Boolean=false",
            // service PID for connecting with the sling resource resolver system
            Constants.SERVICE_PID + "=net.kemuri9.sling.filesystemprovider.impl.FileSystemProvider"
    }
)
@Designate(ocd = FileSystemProviderConfig.class)
public final class FileSystemProvider extends ResourceProvider<FileSystemProviderState> {

    /** configuration */
    private FileSystemProviderConfig config;

    /** slf4j logger */
    private final Logger log;

    @Reference
    private SlingSettingsService slingSettings;

    /** cached instance of the language provider */
    private FileSystemProviderQueryLanguageProvider queryProvider;

    public FileSystemProvider() {
        log = LoggerFactory.getLogger(getClass());
    }

    // OSGi Life cycle methods - START
    @Activate
    protected void activate(ComponentContext context, FileSystemProviderConfig config) {
        log.info("Activate");
        this.config = config;
        log.debug("Sling Home: {}", slingSettings.getSlingHomePath());
        Util.init(slingSettings, config);
    }

    /* Modified should not be included for allowing the Root Path to be altered
       and take effect without restarting the sling system. */

    @Deactivate
    protected void deactivate(ComponentContext context) {
        log.info("deactivate");
        this.config = null;
    }

    @Reference(policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.OPTIONAL,
            policyOption = ReferencePolicyOption.GREEDY)
    protected void setClassLoaderManager(DynamicClassLoaderManager manager) {
        Util.setDynamicClassLoaderManager(manager);
    }

    protected void unsetClassLoaderManager(DynamicClassLoaderManager manager) {
        Util.setDynamicClassLoaderManager(null);
    }
    // OSGi Life cycle methods - End

    @Override
    public <AdapterType> AdapterType adaptTo(ResolveContext<FileSystemProviderState> ctx, Class<AdapterType> type) {
        log.debug("adaptTo({})", type);
        // later
        return null;
    }

    @Override
    public FileSystemProviderState authenticate(Map<String, Object> authenticationInfo) throws LoginException {
        log.debug("authenticating with info {}", authenticationInfo);
        FileSystemProviderState state = new FileSystemProviderState();

        Object authAdmin = authenticationInfo.get(ResourceProvider.AUTH_ADMIN);
        boolean isAdmin = (authAdmin instanceof Boolean) && ((Boolean) authAdmin).booleanValue();
        if (isAdmin) {
            log.debug("requesting an admin session");
            state.username = "admin";
        }

        return state;
    }

    private void checkState(ResolveContext<FileSystemProviderState> ctx) {
        FileSystemProviderState state = ctx.getProviderState();
        if (!state.isLive) {
            throw new IllegalStateException("Provider has been closed");
        }
    }

    @Override
    public void commit(ResolveContext<FileSystemProviderState> ctx) throws PersistenceException {
        log.debug("commit");
    }

    @Override
    public boolean copy(ResolveContext<FileSystemProviderState> ctx, String srcAbsPath, String destAbsPath)
            throws PersistenceException {
        log.debug("copy");
        return true;
    }

    @Override
    public Resource create(ResolveContext<FileSystemProviderState> ctx, String path, Map<String, Object> properties)
            throws PersistenceException {
        log.trace("create({}, {})", path, properties);
        return null;
    }

    @Override
    public void delete(ResolveContext<FileSystemProviderState> ctx, Resource resource) throws PersistenceException {
        log.debug("delete resource at path {}", resource.getPath());
    }

    @Override
    public Object getAttribute(ResolveContext<FileSystemProviderState> ctx, String name) {
        log.trace("getAttribute({})", name);
        if (ResourceResolverFactory.USER.equals(name)) {
            return ctx.getProviderState().username;
        }
        return null;
    }

    @Override
    public Collection<String> getAttributeNames(ResolveContext<FileSystemProviderState> ctx) throws
                IllegalStateException {
        checkState(ctx);
        log.trace("getAttributeNames()");
        return Collections.emptyList();
    }

    @Override
    public QueryLanguageProvider<FileSystemProviderState> getQueryLanguageProvider() {
        return queryProvider;
    }

    /** {@inheritDoc} */
    @Override
    public Resource getResource(ResolveContext<FileSystemProviderState> ctx, String path,
            ResourceContext resourceContext, Resource parent) {
        log.trace("getResource({})", path);
        String absPath = Util.getAbsPath(path);
        log.trace("looking for resource data at '{}'", absPath);
        File resourceFile = new File(absPath);
        if (resourceFile.exists() && resourceFile.isDirectory()) {
            // found a hit for it, return it
            return new FileSystemProviderResource(parent, this, ctx, resourceContext, resourceFile, path);
        }

        // otherwise there is no valid hit
        return null;
    }

    @Override
    public boolean hasChanges(ResolveContext<FileSystemProviderState> ctx) {
        return false;
    }

    @Override
    public boolean isLive(ResolveContext<FileSystemProviderState> ctx) {
        return ctx.getProviderState().isLive;
    }

    @Override
    public Iterator<Resource> listChildren(ResolveContext<FileSystemProviderState> ctx, Resource parent) {
        log.trace("listChildren({})", parent.getPath());
        return null;
    }

    @Override
    public void logout(FileSystemProviderState state) {
        log.debug("logout");
        state.close();
    }

    @Override
    public boolean move(ResolveContext<FileSystemProviderState> ctx, String srcAbsPath, String destAbsPath)
            throws PersistenceException {
        log.debug("move from '{}' to '{}'", srcAbsPath, destAbsPath);
        // moving should be supported
        return true;
    }

    @Override
    public void refresh(ResolveContext<FileSystemProviderState> ctx) {
        log.debug("refresh");
    }

    @Override
    public void revert(ResolveContext<FileSystemProviderState> ctx) {
        log.debug("revert");
    }

    @Override
    public void start(ProviderContext ctx) {
        super.start(ctx);
        log.debug("start");
        queryProvider = new FileSystemProviderQueryLanguageProvider();
        // create the root of the storage, if it doesn't exist
        createRootIfNecessary();
    }

    // create the repository root directory if it does not already exist
    private void createRootIfNecessary() {
        String rootPath = Util.getAbsPath("/");
        File rootDir = new File(rootPath);
        if (rootDir.exists() && !rootDir.isDirectory()) {
            log.error("Root directory '{}' is not a directory!", rootDir.getAbsolutePath());
            return;
        }
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            log.error("Unable to create root directory '{}'", rootDir.getAbsolutePath());
            return;
        }
        // add some properties
    }

    @Override
    public void stop() {
        log.debug("stop");
        super.stop();
    }

    @Override
    public void update(long changeSet) {
        log.debug("update {}", changeSet);
        super.update(changeSet);
    }
}
