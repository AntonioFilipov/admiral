/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;

import io.swagger.models.Info;

import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.host.interceptor.AuthCredentialsInterceptor;
import com.vmware.admiral.host.interceptor.ComputePlacementZoneInterceptor;
import com.vmware.admiral.host.interceptor.EndpointInterceptor;
import com.vmware.admiral.host.interceptor.InUsePlacementZoneInterceptor;
import com.vmware.admiral.host.interceptor.OperationInterceptorRegistry;
import com.vmware.admiral.host.interceptor.ProfileInterceptor;
import com.vmware.admiral.host.interceptor.ResourceGroupInterceptor;
import com.vmware.admiral.host.interceptor.SchedulerPlacementZoneInterceptor;
import com.vmware.admiral.service.common.AuthBootstrapService;
import com.vmware.admiral.service.common.ConfigurationService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.EventTopicRegistrationBootstrapService;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionManager;
import com.vmware.admiral.service.common.NodeMigrationService;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.LoaderFactoryService;
import com.vmware.xenon.common.LoaderService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceConfigUpdateRequest;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.http.netty.NettyHttpListener;
import com.vmware.xenon.common.http.netty.NettyHttpServiceClient;
import com.vmware.xenon.services.common.MigrationTaskService;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.swagger.SwaggerDescriptorService;

/**
 * Stand alone process entry point for management of infrastructure and applications.
 */
public class ManagementHost extends ServiceHost implements IExtensibilityRegistryHost {

    static {
        if (System.getProperty("service.document.version.retention.limit") == null) {
            System.setProperty("service.document.version.retention.limit", "10");
        }
        if (System.getProperty("service.document.version.retention.floor") == null) {
            System.setProperty("service.document.version.retention.floor", "2");
        }
    }

    /**
     * Flag to start a mock adapter instance useful for integration tests
     */
    public boolean startMockHostAdapterInstance;

    /**
     * Users configuration file (full path). Specifying a file automatically enables Xenon's Authx
     * services.
     */
    public String localUsers;

    /**
     * Flag to start an etcd emulator service useful for enabling overlay networking capabilities
     * without an external KV store service
     */
    public boolean startEtcdEmulator;

    /**
     * Uri for clustering traffic. If set, another listener is started, which must be passed as
     * peerNodes to the clustered nodes, and advertised as publicUri.
     */
    public String nodeGroupPublicUri;

    /**
     * File path to key file (same value as ServiceHost.Arguments)
     */
    public Path keyFile;

    /**
     * Key passphrase (same value as ServiceHost.Arguments)
     */
    public String keyPassphrase;

    /**
     * File path to certificate file (same value as ServiceHost.Arguments)
     */
    public Path certificateFile;

    private ExtensibilitySubscriptionManager extensibilityRegistry;

    private static OperationInterceptorRegistry interceptors = new OperationInterceptorRegistry();

    /**
     * Register service operation interceptors.
     */
    static {
        InUsePlacementZoneInterceptor.register(interceptors);
        SchedulerPlacementZoneInterceptor.register(interceptors);
        CompositeComponentInterceptor.register(interceptors);
        AuthCredentialsInterceptor.register(interceptors);
        ResourceGroupInterceptor.register(interceptors);
        EndpointInterceptor.register(interceptors);
        ProfileInterceptor.register(interceptors);
        ComputePlacementZoneInterceptor.register(interceptors);
    }

    public static void main(String[] args) throws Throwable {
        ManagementHost h = new ManagementHost();
        h.initializeHostAndServices(args);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            h.log(Level.WARNING, "Host stopping ...");
            h.stop();
            h.log(Level.WARNING, "Host is stopped");
        }));
    }

    protected ManagementHost initializeHostAndServices(String[] args) throws Throwable {
        log(Level.INFO, "Initializing ...");
        initialize(args);

        log(Level.INFO, "Starting ...");
        start();

        log(Level.INFO, "Setting authorization context ...");
        // Set system user's authorization context to allow the services start privileged access.
        setAuthorizationContext(getSystemAuthorizationContext());

        log(Level.INFO, "**** Management host starting ... ****");

        startFabricServices();
        startManagementServices();
        startClosureServices(this, startMockHostAdapterInstance);
        startSwaggerService();

        log(Level.INFO, "**** Management host started. ****");

        log(Level.INFO, "**** Enabling dynamic service loading... ****");

        enableDynamicServiceLoading();

        log(Level.INFO, "**** Dynamic service loading enabled. ****");
        log(Level.INFO, "**** Migration service starting... ****");
        super.startFactory(new MigrationTaskService());
        // The service need to be privileged in order to not get forbidden during the migration
        // process
        super.addPrivilegedService(NodeMigrationService.class);
        // Clean up authorization context to avoid privileged access.
        setAuthorizationContext(null);

        postInitialization();

        return this;
    }

    @Override
    public ServiceHost initialize(String[] args) throws Throwable {
        CommandLineArgumentParser.parse(this, args);
        Arguments baseArgs = new Arguments();
        if (AuthBootstrapService.isAuthxEnabled(localUsers)) {
            baseArgs.isAuthorizationEnabled = true;
        }
        ServiceHost h = super.initialize(args, baseArgs);
        h.setProcessOwner(true);
        validatePeerArgs();

        ConfigurationState[] configs = ConfigurationService.getConfigurationProperties();
        ConfigurationUtil.initialize(configs);

        return h;
    }

    protected void startFabricServices() throws Throwable {
        this.log(Level.INFO, "Fabric services starting ...");
        HostInitPhotonModelServiceConfig.startServices(this);

        this.log(Level.INFO, "Fabric services started.");
    }

    /**
     * Start all services related to closures support.
     */
    protected void startClosureServices(ServiceHost host, boolean startMockHostAdapterInstance)
            throws Throwable {
        host.log(Level.INFO, "Closure services starting...");
        HostInitClosureServiceConfig.startServices(host, startMockHostAdapterInstance);
        host.log(Level.INFO, "Closure services started.");
    }

    /**
     * Start all services required to support management of infrastructure and applications.
     */
    protected void startCommonServices() throws Throwable {
        this.log(Level.INFO, "Common service starting ...");

        HostInitCommonServiceConfig.startServices(this);
        HostInitAuthServiceConfig.startServices(this);

        registerForServiceAvailability(AuthBootstrapService.startTask(this), true,
                AuthBootstrapService.FACTORY_LINK);

        this.log(Level.INFO, "Common services started.");
    }

    /**
     * Start all services required to support management of infrastructure and applications.
     */
    protected void startManagementServices() throws Throwable {
        this.log(Level.INFO, "Management service starting ...");

        registerForServiceAvailability(CaSigningCertService.startTask(this), true,
                CaSigningCertService.FACTORY_LINK);

        registerForServiceAvailability(EventTopicRegistrationBootstrapService.startTask(this), true,
                EventTopicRegistrationBootstrapService.FACTORY_LINK);

        HostInitComputeServicesConfig.startServices(this, false);
        HostInitComputeBackgroundServicesConfig.startServices(this);
        HostInitRequestServicesConfig.startServices(this);
        HostInitImageServicesConfig.startServices(this);
        HostInitUiServicesConfig.startServices(this);
        HostInitDockerAdapterServiceConfig.startServices(this, startMockHostAdapterInstance);
        HostInitKubernetesAdapterServiceConfig.startServices(this, startMockHostAdapterInstance);
        HostInitEtcdAdapterServiceConfig.startServices(this, startEtcdEmulator);
        HostInitContinuousDeliveryServicesConfig.startServices(this);
        HostInitRegistryAdapterServiceConfig.startServices(this);

        this.log(Level.INFO, "Management services started.");
    }

    /**
     * Start Swagger service.
     */
    protected void startSwaggerService() {
        this.log(Level.INFO, "Swagger service starting ...");

        // Serve Swagger 2.0 compatible API description
        SwaggerDescriptorService swagger = new SwaggerDescriptorService();

        // Exclude some core services
        swagger.setExcludedPrefixes(
                "/core/transactions",
                "/core/node-groups");
        swagger.setExcludeUtilities(true);

        // Provide API metainfo
        Info apiInfo = new Info();
        apiInfo.setVersion("0.0.1");
        apiInfo.setTitle("Container Management");

        // TODO - TBD
        // apiInfo.setLicense(new License().name("Apache 2.0")
        // .url("https://github.com/vmware/xenon/blob/master/LICENSE"));
        // apiInfo.setContact(new Contact().url("https://github.com/vmware/xenon"));

        swagger.setInfo(apiInfo);

        // Serve swagger on default uri
        this.startService(swagger);
        this.log(Level.INFO, "Swagger service started. Checkout Swagger UI at: "
                + this.getUri() + ServiceUriPaths.SWAGGER + "/ui");
    }

    private void startExtensibilityRegistry() {
        extensibilityRegistry = new ExtensibilitySubscriptionManager();
        this.startService(extensibilityRegistry);
    }

    /**
     * The directory from which services are dynamically loaded; see
     * {@link #enableDynamicServiceLoading()}
     */
    private static final String DYNAMIC_SERVICES_PATH = System.getProperty(
            ManagementHost.class.getPackage().getName() + ".dynamic_services_path",
            "/etc/xenon/dynamic-services");

    /**
     * Enable Xenon services to be dynamically loaded, by starting the LoaderService. TODO: This
     * code is not required for Admiral, but rather for other components that are implemented as
     * Xenon services, so most of this host startup logic could be extracted out of "admiral" into a
     * separate component that just starts Xenon, and then "admiral" and other components could just
     * instruct Xenon to load their services.
     */
    void enableDynamicServiceLoading() {
        // https://github.com/vmware/xenon/wiki/LoaderService#loader-service-host
        // 1. start the loader service
        startService(
                Operation.createPost(
                        UriUtils.buildUri(
                                this,
                                LoaderFactoryService.class)),
                new LoaderFactoryService());
        // 2. configure service discovery from DYNAMIC_SERVICES_PATH
        LoaderService.LoaderServiceState payload = new LoaderService.LoaderServiceState();
        payload.loaderType = LoaderService.LoaderType.FILESYSTEM;
        payload.path = DYNAMIC_SERVICES_PATH;
        payload.servicePackages = new HashMap<>();
        sendRequest(Operation.createPost(UriUtils.buildUri(this, LoaderFactoryService.class))
                .setBody(payload)
                .setReferer(getUri()));
    }

    private void validatePeerArgs() throws Throwable {
        if (nodeGroupPublicUri != null) {
            URI uri = new URI(nodeGroupPublicUri);

            if (this.getPort() != -1 && uri.getPort() == this.getPort()) {
                throw new IllegalArgumentException("--nodeGroupPublicUri port must be different"
                        + " from --port");
            }

            if (this.getSecurePort() != -1 && uri.getPort() == this.getSecurePort()) {
                throw new IllegalArgumentException("--nodeGroupPublicUri port must be different"
                        + " from --securePort");
            }

            if (uri.getPort() < 0 || uri.getPort() >= Short.MAX_VALUE * 2) {
                throw new IllegalArgumentException("--nodeGroupPublicUri port is not in range");
            }

            if (uri.getScheme() == null) {
                throw new IllegalArgumentException("--nodeGroupPublicUri scheme must be set");
            }

            if (uri.getHost() == null) {
                throw new IllegalArgumentException("--nodeGroupPublicUri host must be set");
            }
        }
    }

    @Override
    public ServiceHost startFactory(Service service) {
        interceptors.subscribeToService(service);
        return super.startFactory(service);
    }

    @Override
    public ServiceHost startService(Operation post, Service service) {
        interceptors.subscribeToService(service);
        return super.startService(post, service);
    }

    @Override
    public ServiceHost start() throws Throwable {
        // Only initialize ServerX509TrustManager
        ServerX509TrustManager trustManager = ServerX509TrustManager.init(this);
        ServiceClient serviceClient = createServiceClient(CertificateUtil.createSSLContext(
                trustManager, null), 0);
        setClient(serviceClient);
        super.start();

        startDefaultCoreServicesSynchronously();
        startPeerListener();

        log(Level.INFO, "Setting authorization context ...");
        // Set system user's authorization context to allow the services start privileged access.
        setAuthorizationContext(getSystemAuthorizationContext());

        startCommonServices();

        startExtensibilityRegistry();

        // now start ServerX509TrustManager
        trustManager.start();
        setAuthorizationContext(null);
        return this;
    }

    /**
     * Execute code after all services are started.
     */
    private void postInitialization() {
        // hack to set new retention limits for 2 services from photon model
        //   - resource pool - placement zones
        //   - groups placement - placements
        registerForServiceAvailability(
                (o, e) -> {
                    setNewLimits(GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK);
                    setNewLimits(GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK);
                },
                GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK,
                GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK);
    }

    private void setNewLimits(String service) {
        log(Level.INFO, "Set new retention limit for %s", service);
        ServiceConfigUpdateRequest configUpdate = ServiceConfigUpdateRequest.create();
        configUpdate.versionRetentionLimit = 5L;
        configUpdate.versionRetentionFloor = 5L;
        Operation.createPatch(UriUtils.buildConfigUri(this, service))
                .setBodyNoCloning(configUpdate)
                .setReferer(this.getPublicUri())
                .sendWith(this);
    }

    private ServiceClient createServiceClient(SSLContext sslContext,
            int requestPayloadSizeLimit) {
        try {
            // Use the class name and prefix of GIT commit ID as the user agent name and version
            String commitID = (String) getState().codeProperties
                    .get(GIT_COMMIT_SOURCE_PROPERTY_COMMIT_ID);
            if (commitID == null) {
                throw new LocalizableValidationException("CommitID code property not found!",
                        "host.commit.id.not.found");
            }
            commitID = commitID.substring(0, 8);
            String userAgent = ServiceHost.class.getSimpleName() + "/" + commitID;
            ServiceClient serviceClient = NettyHttpServiceClient.create(userAgent,
                    null,
                    getScheduledExecutor(),
                    this);
            if (requestPayloadSizeLimit > 0) {
                serviceClient.setRequestPayloadSizeLimit(requestPayloadSizeLimit);
            }
            serviceClient.setSSLContext(sslContext);

            return serviceClient;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to create ServiceClient", e);
        }
    }

    private void startPeerListener() throws Throwable {
        if (nodeGroupPublicUri != null) {
            URI uri = new URI(nodeGroupPublicUri);
            NettyHttpListener peerListener = new NettyHttpListener(this);
            if (UriUtils.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                peerListener.setSSLContextFiles(certificateFile.toUri(),
                        keyFile.toUri(), keyPassphrase);
            }
            peerListener.start(uri.getPort(), uri.getHost());
        }
    }

    @Override
    public void setAuthorizationContext(AuthorizationContext context) {
        super.setAuthorizationContext(context);
    }

    @Override
    public AuthorizationContext getSystemAuthorizationContext() {
        return super.getSystemAuthorizationContext();
    }

    @Override
    public ExtensibilitySubscriptionManager getExtensibilityRegistry() {
        return extensibilityRegistry;
    }

}
