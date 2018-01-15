/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.FileUtils.ResourceEntry;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.ServiceUriPaths;

public abstract class BaseUiService extends StatelessService {
    public static final String HTML_RESOURCE_EXTENSION = ".html";
    public static final String LOGIN_PATH = ManagementUriParts.LOGIN;
    public static final String INDEX_PATH = "index" + HTML_RESOURCE_EXTENSION;
    public static final String I18NEXT_COOKIE = "i18next";

    @Override
    public void authorizeRequest(Operation op) {
        // No authorization required. In case the user is not authorized, when retrieving the / we
        // will redirect to login.
        op.complete();
    }

    @Override
    public void handleStart(Operation startPost) {
        try {
            startUiFileContentServices();
            super.handleStart(startPost);
        } catch (Throwable e) {
            startPost.fail(e);
        }
    }

    @Override
    public void handleGet(Operation get) {
        URI uri = get.getUri();
        String selfLink = getSelfLink();
        String requestUri = uri.getPath();

        String selfLinkWithTrailing = selfLink;
        if (!selfLink.endsWith(UriUtils.URI_PATH_CHAR)) {
            selfLinkWithTrailing += UriUtils.URI_PATH_CHAR;
        }

        if (selfLink.equals(requestUri) && !selfLinkWithTrailing.equals(requestUri)) {
            // no trailing /, redirect to a location with trailing /
            get.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
            get.addResponseHeader(Operation.LOCATION_HEADER, selfLinkWithTrailing);
            get.complete();
            return;
        } else if (requestUri.equals(selfLinkWithTrailing)) {
            String indexFileName = ServiceUriPaths.UI_RESOURCE_DEFAULT_FILE;
            String uiResourcePath = selfLinkWithTrailing + indexFileName;
            Operation operation = get.clone();
            operation.setUri(UriUtils.buildUri(getHost(), uiResourcePath, uri.getQuery()))
                    .setCompletion((o, e) -> {
                        // Localization - browser language user preferences
                        String acceptLanguage = get
                                .getRequestHeader(Operation.ACCEPT_LANGUAGE_HEADER);

                        if (acceptLanguage != null && !acceptLanguage.trim().isEmpty()) {
                            List<Locale.LanguageRange> parsed = Locale.LanguageRange
                                    .parse(acceptLanguage);

                            if (parsed.size() > 0) {
                                get.addResponseCookie(I18NEXT_COOKIE, parsed.get(0).getRange() +
                                        ((getHost().getSecurePort() > 0) ? "; Secure" : ""));
                            }
                        }

                        get.setBody(o.getBodyRaw())
                                .setStatusCode(o.getStatusCode())
                                .setContentType(o.getContentType());
                        if (e != null) {
                            get.fail(e);
                        } else {
                            get.complete();
                        }
                    });

            getHost().sendRequest(operation);
        }
    }

    // As defined in ServiceHost
    protected void startUiFileContentServices() throws Throwable {
        Map<Path, String> pathToURIPath = new HashMap<>();

        Path baseResourcePath = Utils.getServiceUiResourcePath(this);
        try {
            pathToURIPath = discoverUiResources(baseResourcePath, this);
        } catch (Throwable e) {
            log(Level.WARNING, "Error enumerating UI resources for %s: %s", this.getSelfLink(),
                    Utils.toString(e));
        }

        if (pathToURIPath.isEmpty()) {
            log(Level.WARNING, "No custom UI resources found for %s", this.getClass().getName());
            return;
        }

        for (Entry<Path, String> e : pathToURIPath.entrySet()) {
            String value = e.getValue();

            if (value.contains("/META-INF/")) {
                continue;
            }

            Operation post = Operation
                    .createPost(UriUtils.buildUri(getHost(), value));
            RestrictiveFileContentService fcs = new RestrictiveFileContentService(
                    e.getKey().toFile());
            getHost().startService(post, fcs);
        }
    }

    // Find UI resources for this service (e.g. html, css, js)
    protected Map<Path, String> discoverUiResources(Path path, Service s)
            throws Throwable {
        Map<Path, String> pathToURIPath = new HashMap<>();
        Path baseUriPath = Paths.get(getSelfLink());

        String prefix = path.toString().replace('\\', '/');

        if (getHost().getState().resourceSandboxFileReference != null) {
            discoverFileResources(s, pathToURIPath, baseUriPath, prefix);
        }

        if (pathToURIPath.isEmpty()) {
            discoverJarResources(path, s, pathToURIPath, baseUriPath, prefix);
        }
        return pathToURIPath;
    }

    private void discoverJarResources(Path path, Service s, Map<Path, String> pathToURIPath,
            Path baseUriPath, String prefix) throws URISyntaxException, IOException {
        for (ResourceEntry entry : FileUtils.findResources(s.getClass(), prefix)) {
            Path resourcePath = path.resolve(entry.suffix);
            Path uriPath = baseUriPath.resolve(entry.suffix);
            Path outputPath = getHost().copyResourceToSandbox(entry.url, resourcePath);
            if (outputPath == null) {
                // Failed to copy one resource, disable user interface for this service.
                s.toggleOption(ServiceOption.HTML_USER_INTERFACE, false);
            } else {
                pathToURIPath.put(outputPath, uriPath.toString().replace('\\', '/'));
            }
        }
    }

    private void discoverFileResources(Service s, Map<Path, String> pathToURIPath,
            Path baseUriPath,
            String prefix) {
        File rootDir = new File(new File(getHost().getState().resourceSandboxFileReference),
                prefix);
        if (!rootDir.exists()) {
            log(Level.INFO, "Resource directory not found: %s", rootDir.toString());
            return;
        }

        String basePath = baseUriPath.toString();
        String serviceName = s.getClass().getSimpleName();
        List<File> resources = FileUtils.findFiles(rootDir.toPath(),
                new HashSet<String>(), false);
        for (File f : resources) {
            String subPath = f.getAbsolutePath();
            subPath = subPath.substring(subPath.indexOf(serviceName));
            subPath = subPath.replace(serviceName, "");
            Path uriPath = Paths.get(basePath, subPath);
            pathToURIPath.put(f.toPath(), uriPath.toString().replace('\\', '/'));
        }

        if (pathToURIPath.isEmpty()) {
            log(Level.INFO, "No resources found in directory: %s", rootDir.toString());
        }
    }

    protected void startForwardingService(String sourcePath, String targetPath) {
        Operation post = Operation
                .createPost(UriUtils.buildUri(getHost(), sourcePath));
        UiNgResourceForwarding rf = new UiNgResourceForwarding(sourcePath, targetPath);
        getHost().startService(post, rf);
    }

    protected void startRedirectService(String sourcePath, String targetPath) {
        Operation post = Operation
                .createPost(UriUtils.buildUri(getHost(), sourcePath));
        UiNgResourceForwarding rf = new UiNgResourceForwarding(sourcePath, targetPath, true);
        getHost().startService(post, rf);
    }

    protected static boolean redirectToLoginOrIndex(ServiceHost host, Operation op) {
        // in embedded mode we are already authenticated
        // no need to show login or home page upon successful login
        if (ConfigurationUtil.isEmbedded()) {
            return false;
        }

        if (host.isAuthorizationEnabled()) {
            AuthorizationContext ctx = op.getAuthorizationContext();
            if (ctx == null) {
                // It should never happen. If no credentials are provided then Xenon falls back
                // on the guest user authorization context and claims.
                op.fail(new IllegalStateException("ctx == null"));
                return true;
            }

            Claims claims = ctx.getClaims();
            if (claims == null) {
                // It should never happen. If no credentials are provided then Xenon falls back
                // on the guest user authorization context and claims.
                op.fail(new IllegalStateException("claims == null"));
                return true;
            }
            String path = op.getUri().getPath();

            // Is the user trying to login?
            boolean isLoginPage = path.endsWith(LOGIN_PATH);

            // Is the user trying to access an html page? No need to redirect requests to js,
            // css, etc.
            boolean isHTMLResource = !path.contains(".") ||
                    path.endsWith(HTML_RESOURCE_EXTENSION);

            // Is the user already authenticated?
            boolean isValidUser = (claims.getSubject() != null)
                    && !GuestUserService.SELF_LINK.equals(claims.getSubject());

            boolean loginRequired = !isLoginPage && isHTMLResource && !isValidUser;
            boolean showHomePage = isLoginPage && isValidUser;

            if (loginRequired) {
                // Redirect the browser to the login page cleanup session data.
                AuthUtils.cleanupSessionData(op);
                String location = ManagementUriParts.UI_SERVICE + LOGIN_PATH;
                location = location.replaceAll("//", "/");
                op.addResponseHeader(Operation.LOCATION_HEADER, location);
                op.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
                op.complete();

                return true;
            } else if (showHomePage) {
                // Redirect the browser to the home page
                String location = ManagementUriParts.UI_SERVICE;
                op.addResponseHeader(Operation.LOCATION_HEADER, location);
                op.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
                op.complete();

                return true;
            }
        }
        return false;
    }

    public static class UiNgResourceForwarding extends StatelessService {

        private String sourcePath;
        private String targetPath;
        private boolean redirect;

        public UiNgResourceForwarding(String sourcePath, String targetPath) {
            this(sourcePath, targetPath, false);
        }

        public UiNgResourceForwarding(String sourcePath, String targetPath, boolean redirect) {
            this.sourcePath = sourcePath;
            this.targetPath = targetPath;
            this.redirect = redirect;
            setSelfLink(sourcePath);

            this.options.add(ServiceOption.URI_NAMESPACE_OWNER);
        }

        @Override
        public void authorizeRequest(Operation op) {
            // No authorization required.
            op.complete();
        }

        @Override
        public void handleGet(Operation get) {
            if (redirectToLoginOrIndex(getHost(), get)) {
                return;
            }

            URI uri = get.getUri();
            if (redirect) {
                String path = uri.getPath();
                path = path.replace(this.sourcePath, this.targetPath);

                String query = uri.getRawQuery();
                if (query != null) {
                    path += "?" + query;
                }

                get.addResponseHeader(Operation.LOCATION_HEADER, path);
                get.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
                get.complete();
                return;
            }

            String uriPath = uri.getPath();
            uriPath = uriPath.replace(this.sourcePath, this.targetPath);

            if (uriPath.endsWith(targetPath) && uriPath.endsWith(UriUtils.URI_PATH_CHAR)) {
                uriPath += INDEX_PATH;
            }

            Operation operation = get.clone();
            operation.setUri(UriUtils.buildUri(getHost(), uriPath))
                    .setCompletion((o, e) -> {
                        get.setBody(o.getBodyRaw())
                                .setStatusCode(o.getStatusCode())
                                .setContentType(o.getContentType());
                        get.transferResponseHeadersFrom(o);
                        if (e != null) {
                            get.fail(e);
                        } else {
                            get.complete();
                        }
                    });

            sendRequest(operation);
        }
    }
}
