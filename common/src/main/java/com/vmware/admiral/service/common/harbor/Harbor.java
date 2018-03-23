/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common.harbor;

import java.util.regex.Pattern;

import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.xenon.common.UriUtils;

public interface Harbor {

    String ENDPOINT_REPOSITORIES = "/repositories";
    String ENDPOINT_PROJECTS = "/projects";

    String QUERY_PARAM_PROJECT_ID = "project_id";
    String QUERY_PARAM_DETAIL = "detail";
    String RESP_PROP_ID = "id";
    String RESP_PROP_NAME = "name";
    String RESP_PROP_TAGS_COUNT = "tags_count";

    String CONFIGURATION_URL_PROPERTY_NAME = "harbor.tab.url";
    String CONFIGURATION_USER_PROPERTY_NAME = "harbor.user";
    String CONFIGURATION_PASS_PROPERTY_NAME = "harbor.password";
    String API_BASE_ENDPOINT = "api";
    String I18N_RESOURCE_SUBPATH = "i18n/lang";

    String DEFAULT_REGISTRY_NAME = "default-vic-registry";
    String DEFAULT_REGISTRY_LINK = UriUtils.buildUriPath(RegistryFactoryService.SELF_LINK,
            DEFAULT_REGISTRY_NAME);
    String DEFAULT_REGISTRY_USER_PREFIX = "vic-registry-";

    String PROJECTS_DELETE_VERIFICATION_SUFFIX = "_deletable";

    Pattern PROJECT_NAME_PATTERN = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

}
