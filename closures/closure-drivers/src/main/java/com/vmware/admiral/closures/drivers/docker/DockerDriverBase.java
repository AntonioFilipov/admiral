/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.closures.drivers.docker;

import static com.vmware.admiral.service.common.SslTrustCertificateService.SSL_TRUST_LAST_UPDATED_DOCUMENT_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.closures.drivers.ClosureDockerClient;
import com.vmware.admiral.closures.drivers.ClosureDockerClientFactory;
import com.vmware.admiral.closures.drivers.ContainerConfiguration;
import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.closures.drivers.ExecutionDriver;
import com.vmware.admiral.closures.drivers.ImageConfiguration;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.util.ClosureProps;
import com.vmware.admiral.closures.util.ClosureUtils;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceDocumentQuery.ServiceDocumentQueryElementResult;
import com.vmware.admiral.common.util.SubscriptionManager;
import com.vmware.admiral.service.common.ConfigurationService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Base of execution drivers using docker service.
 *
 */
public abstract class DockerDriverBase implements ExecutionDriver {

    private static final String SSL_TRUST_CONFIG_SUBSCRIBE_FOR_LINK = UriUtils.buildUriPath(
            ConfigurationService.ConfigurationFactoryService.SELF_LINK,
            SSL_TRUST_LAST_UPDATED_DOCUMENT_KEY);

    private static String CLOSURE_SERVICE_CALLBACK_URI = getConfigProperty(
            ClosureProps.CLOSURE_SERVICE_CALLBACK_URI);

    private final ServiceHost serviceHost;
    private final DriverRegistry driverRegistry;
    private final ClosureDockerClientFactory dockerClientFactory;

    private SubscriptionManager<ConfigurationService.ConfigurationState> subscriptionManager;
    private ServiceDocumentQuery<SslTrustCertificateState> sslTrustQuery;
    private ClosureSslTrustQueryCompletionHandler queryHandler;
    private volatile long documentUpdateTimeMicros;

    private AtomicReference<String> trustCertificates;

    public abstract String getDockerImage();

    public DockerDriverBase(ServiceHost serviceHost, DriverRegistry driverRegistry,
            ClosureDockerClientFactory dockerClientFactory) {
        this.serviceHost = serviceHost;
        this.driverRegistry = driverRegistry;
        this.dockerClientFactory = dockerClientFactory;

        this.trustCertificates = new AtomicReference<>();

        this.subscriptionManager = new SubscriptionManager<>(serviceHost,
                serviceHost.getId() + getDockerImage(), SSL_TRUST_CONFIG_SUBSCRIBE_FOR_LINK,
                ConfigurationService.ConfigurationState.class, true);

        this.sslTrustQuery = new ServiceDocumentQuery<SslTrustCertificateState>(serviceHost,
                SslTrustCertificateState.class);
        queryHandler = new ClosureSslTrustQueryCompletionHandler();

        subscribeForSslTrustCertNotifications();
        loadSslTrustCertServices();
    }

    private void subscribeForSslTrustCertNotifications() {
        this.subscriptionManager.start((n) -> {
            loadSslTrustCertServices();
        }, null);
    }

    private void loadSslTrustCertServices() {
        // make sure first get the time, then issue the query not to miss an interval with updates
        logInfo("reloadSslTrustCertServices........" + this.documentUpdateTimeMicros);

        long currentDocumentUpdateTimeMicros = Utils.getNowMicrosUtc();
        sslTrustQuery.queryUpdatedSince(documentUpdateTimeMicros, queryHandler);
        this.documentUpdateTimeMicros = currentDocumentUpdateTimeMicros;
    }

    private class ClosureSslTrustQueryCompletionHandler implements
            Consumer<ServiceDocumentQueryElementResult<SslTrustCertificateState>> {

        Set<String> trustedCerts = new HashSet<>();

        @Override
        public void accept(ServiceDocumentQueryElementResult<SslTrustCertificateState> result) {
            if (result.hasException()) {
                Utils.logWarning("Exception during ssl trust cert loading: %s",
                        (result.getException() instanceof CancellationException)
                                ? result.getException().getClass().getName()
                                : Utils.toString(result.getException()));
            } else if (result.hasResult()) {
                SslTrustCertificateState sslTrustCert = result.getResult();
                trustedCerts.add(sslTrustCert.certificate);
            } else {
                StringBuilder sb = new StringBuilder();
                for (String certificate : trustedCerts) {
                    sb.append('\n').append(certificate);
                }

                trustCertificates.set(ClosureUtils.compress((sb.toString())));
            }
        }

    }

    @Override
    public void executeClosure(Closure closure, ClosureDescription closureDesc, String token,
            Consumer<Throwable> errorHandler) {
        ClosureDockerClient dockerClient = dockerClientFactory.getClient();

        String containerName = generateContainerName(closure);

        ContainerConfiguration configuration = new ContainerConfiguration(containerName);

        configuration.memoryMB = closureDesc.resources.ramMB;
        configuration.cpuShares = closureDesc.resources.cpuShares;

        configuration.logConfiguration = closureDesc.logConfiguration;
        configuration.sourceURL = closureDesc.sourceURL;
        configuration.dependencies = closureDesc.dependencies;

        List<String> vars = populateEnvs(closure, token);
        configuration.envVars = vars.toArray(new String[vars.size()]);
        logInfo("Creating closure with envs: %s", vars.get(0));

        String containerImage = getDockerImage();

        ImageConfiguration imageConfig = new ImageConfiguration();
        imageConfig.imageName = containerImage;
        String imageVersion = driverRegistry.getImageVersion(closureDesc.runtime);
        imageConfig.imageNameVersion = ClosureUtils.prepareImageTag(configuration, imageVersion);
        imageConfig.baseImageName = containerImage + "_base";
        imageConfig.baseImageVersion = driverRegistry.getBaseImageVersion(closureDesc.runtime);

        imageConfig.registry = getConfigProperty(
                ClosureProps.CLOSURE_RUNTIME_IMAGE_REGISTRY + closureDesc.runtime);

        logInfo("Creating container with name: %s image: %s", containerName, containerImage);
        dockerClient.createAndStartContainer(closure, imageConfig, configuration, errorHandler);
        logInfo("Code execution request sent.");
    }

    private static String getConfigProperty(String propertyName) {
        return ConfigurationUtil.getProperty(propertyName);
    }

    @Override
    public void cleanClosure(Closure closure, Consumer<Throwable> errorHandler) {
        ClosureDockerClient dockerClient = dockerClientFactory.getClient();
        if (dockerClient == null) {
            Utils.logWarning(
                    "No available docker clients found! Unable to clean execution container!");
            return;
        }

        if (closure.resourceLinks == null || closure.resourceLinks.size() <= 0) {
            errorHandler.accept(new Exception(
                    "No resource to clean for closure: " + closure.documentSelfLink));
            return;
        }

        logInfo("Killing container with for closure: %s", closure.documentSelfLink);
        for (String containerLink : closure.resourceLinks) {
            try {
                logInfo("Removing container with Id: %s", containerLink);
                dockerClient.removeContainer(containerLink, errorHandler);
                logInfo("Closure cancelled: %s", closure.documentSelfLink);
            } catch (Exception ex) {
                Utils.logWarning("Unable to clean containers corresponding to cancelled"
                                + " closure: %s. Error: %s",
                        closure.documentSelfLink, Utils.toString(ex));
            }
        }
    }

    @Override
    public void cleanImage(String imageName, String computeStateLink,
            Consumer<Throwable> errorHandler) {
        ClosureDockerClient dockerClient = dockerClientFactory.getClient();
        if (dockerClient == null) {
            errorHandler.accept(new Exception(
                    "No available docker clients found! Unable to clean docker image!"));
            return;
        }

        if (computeStateLink == null || computeStateLink.length() <= 0) {
            errorHandler.accept(new Exception("No compute state provided!"));
            return;
        }
        if (imageName == null || imageName.length() <= 0) {
            errorHandler.accept(new Exception("No image name provided!"));
            return;
        }

        dockerClient.cleanImage(imageName, computeStateLink, errorHandler);
    }

    @Override
    public void inspectImage(String imageName, String imageRequestLink,
            Consumer<Throwable> errorHandler) {
        ClosureDockerClient dockerClient = dockerClientFactory.getClient();
        if (dockerClient == null) {
            errorHandler.accept(new Exception(
                    "No available docker clients found! Unable to clean docker image!"));
            return;
        }

        if (imageRequestLink == null || imageRequestLink.length() <= 0) {
            String errorMsg = "No image request link provided!";
            Utils.logWarning(errorMsg);
            errorHandler.accept(new Exception(errorMsg));
            return;
        }

        dockerClient.inspectImage(imageName, imageRequestLink, errorHandler);
    }

    @Override
    public ServiceHost getServiceHost() {
        return serviceHost;
    }

    private String generateContainerName(Closure closure) {
        String taskID = Service.getId(closure.documentSelfLink);
        return taskID + "_" + closure.documentVersion;
    }

    private List<String> populateEnvs(Closure closure, String token) {
        List<String> vars = new ArrayList<>();

        URI callbackUri = prepareCallbackUri(closure);
        vars.add(ClosureProps.ENV_PROP_TASK_URI + "=" + callbackUri);
        logInfo("Setting TASK_URI %s for closure: %s", callbackUri, closure.descriptionLink);
        if (!ClosureUtils.isEmpty(token)) {
            vars.add(ClosureProps.ENV_PROP_TOKEN + "=" + token);
        }
        String certs = trustCertificates.get();

        certs = certs == null ? "" : certs;
        vars.add(ClosureProps.ENV_TRUST_CERTS + "=" + certs);

        return vars;
    }

    private URI prepareCallbackUri(Closure closure) {
        URI callbackUri = null;
        if (CLOSURE_SERVICE_CALLBACK_URI != null) {
            callbackUri = buildConfiguredCallbackUri(CLOSURE_SERVICE_CALLBACK_URI,
                    closure.documentSelfLink);
        }

        if (callbackUri == null) {
            // fallback to publicUri as defined in xenon
            callbackUri = UriUtils.buildPublicUri(getServiceHost(), closure.documentSelfLink);
        }
        logFine("Computed closure callback URI: %s, closure: %s", callbackUri, closure
                .documentSelfLink);
        return callbackUri;
    }

    public URI buildConfiguredCallbackUri(String callbackUri, String linkPath) {
        try {
            if (callbackUri != null) {
                if (callbackUri.endsWith("/")) {
                    callbackUri = callbackUri.substring(0, callbackUri.lastIndexOf('/'));
                }
                return URI.create(callbackUri + linkPath);
            }
        } catch (Throwable e) {
            Utils.log(Utils.class, DockerDriverBase.class.getSimpleName(), Level.SEVERE,
                    "Failure in building callback uri %s, %s, %s", callbackUri, linkPath, Utils
                            .toString(e));
        }
        return null;
    }

    private void logInfo(String message, Object... values) {
        Utils.log(getClass(), getClass().getSimpleName(), Level.INFO, message, values);
    }

    private void logFine(String message, Object... values) {
        Utils.log(getClass(), getClass().getSimpleName(), Level.FINE, message, values);
    }

}
