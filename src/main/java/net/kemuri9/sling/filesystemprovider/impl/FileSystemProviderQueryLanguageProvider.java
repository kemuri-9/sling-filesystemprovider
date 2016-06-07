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

import java.util.Iterator;

import org.apache.sling.api.SlingException;
import org.apache.sling.api.resource.QuerySyntaxException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.spi.resource.provider.QueryLanguageProvider;
import org.apache.sling.spi.resource.provider.ResolveContext;

/**
 * Query Languages supported by the file system provider.
 * Currently there are none.
 * XPATH may be a possible candidate in the future.
 */
final class FileSystemProviderQueryLanguageProvider implements QueryLanguageProvider<FileSystemProviderState> {

    @Override
    public String[] getSupportedLanguages(ResolveContext<FileSystemProviderState> ctx) {
        return new String[0];
    }

    @Override
    public Iterator<Resource> findResources(ResolveContext<FileSystemProviderState> ctx, String query,
            String language) throws QuerySyntaxException, SlingException, IllegalStateException {
        checkState(ctx);
        throw new QuerySyntaxException("language " + language + " is unsupported", query, language);
    }

    @Override
    public Iterator<ValueMap> queryResources(ResolveContext<FileSystemProviderState> ctx, String query,
            String language) throws QuerySyntaxException, SlingException, IllegalStateException {
        checkState(ctx);
        throw new QuerySyntaxException("language " + language + " is unsupported", query, language);
    }

    private void checkState(ResolveContext<FileSystemProviderState> ctx) {
        FileSystemProviderState state = ctx.getProviderState();
        if (!state.isLive) {
            throw new IllegalStateException("Provider has been closed");
        }
    }
}
