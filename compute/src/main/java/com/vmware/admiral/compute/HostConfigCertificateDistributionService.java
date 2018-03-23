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

package com.vmware.admiral.compute;

import static com.vmware.admiral.common.util.CertificateUtilExtended.isSelfSignedCertificate;

import java.io.File;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.CertificateUtilExtended;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.harbor.Harbor;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;

/**
 * Service for distribution of self-signed trusted registry certificates to a single docker host.
 */
public class HostConfigCertificateDistributionService extends
        AbstractCertificateDistributionService {
    public static final String SELF_LINK = ManagementUriParts.CERT_DISTRIBUTION_ADD_HOST;

    protected volatile String vicCertificateChain;
    protected volatile String harborUrl;
    protected volatile Boolean isVic;

    public static class HostConfigCertificateDistributionState {
        public String hostLink;
        public List<String> tenantLinks;
    }

    @Override
    public void handlePost(Operation op) {
        try {
            HostConfigCertificateDistributionState distState = op
                    .getBody(HostConfigCertificateDistributionState.class);

            AssertUtil.assertNotNull(distState.hostLink, "hostLink");

            op.complete();

            handleAddDockerHostOperation(distState.hostLink, distState.tenantLinks);
        } catch (Throwable t) {
            logSevere("Failed to process certificate distribution request. %s", Utils.toString(t));
            op.fail(t);
        }
    }

    private void handleAddDockerHostOperation(String hostLink, List<String> tenantLinks) {
        handleVicCertificate(hostLink);

        sendRequest(Operation.createGet(this, RegistryFactoryService.SELF_LINK)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to retrieve registry links. %s", Utils.toString(e));
                        return;
                    }

                    ServiceDocumentQueryResult body = o.getBody(ServiceDocumentQueryResult.class);

                    logFine("Distributing certificates for [%s]", body.documentLinks);
                    for (String registryLink : body.documentLinks) {
                        fetchRegistryState(registryLink, (registry) -> {
                            RegistryService.fetchRegistryCertificate(registry, (cert) -> {
                                if (!isSelfSignedCertificate(cert)) {
                                    logInfo("Skip certificate distribution for registry [%s]: "
                                            + "certificate not self-signed.",
                                            registryLink);
                                    return;
                                }
                                uploadCertificate(hostLink, registry.address, cert, tenantLinks);
                            }, getHost());
                        });
                    }
                }));
    }

    private void fetchRegistryState(String registryLink, Consumer<RegistryState> callback) {
        sendRequest(Operation.createGet(this, registryLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to retrieve registry state for %s. %s",
                                registryLink, Utils.toString(e));
                        return;
                    }
                    RegistryState registry = o.getBody(RegistryState.class);

                    callback.accept(registry);
                }));
    }

    /**
     * When part of VIC product upload Admiral certificate to the respective Docker host to
     * enable communication with Harbor. Admiral and Harbor certificates are the same.
     */
    protected void handleVicCertificate(String hostLink) {
        if (isVic == null) {
            ConfigurationUtil.getConfigProperty(this, ConfigurationUtil.VIC_MODE_PROPERTY,
                    (vic) -> {
                        isVic = Boolean.valueOf(vic);
                        handleVicCertificate(hostLink);
                    });
            return;
        }

        if (!isVic) {
            return;
        }

        if (harborUrl == null) {
            ConfigurationUtil.getConfigProperty(this, Harbor.CONFIGURATION_URL_PROPERTY_NAME,
                    (harborTabUrl) -> {
                        if (harborTabUrl == null) {
                            return;
                        }

                        this.harborUrl = harborTabUrl;
                        handleVicCertificate(hostLink);
                    });
            return;
        }

        if (vicCertificateChain == null) {
            URI certFileUri = getHost().getState().certificateFileReference;
            if (certFileUri == null) {
                return;
            }

            X509Certificate[] certChain = CertificateUtilExtended.fromFile(new File(certFileUri));
            if (certChain != null) {
                vicCertificateChain = CertificateUtilExtended.toPEMformat(certChain, getHost());
            }
        }

        if (vicCertificateChain != null && harborUrl != null) {
            uploadCertificate(hostLink, harborUrl, vicCertificateChain, null);
        }
    }

}
