/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Docker Compose service network configuration
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceNetwork {

    public static final String FIELD_NAME = "name";
    public static final String FIELD_NAME_ALIASES = "aliases";
    public static final String FIELD_NAME_IP4_ADDRESS = "ipv4_address";
    public static final String FIELD_NAME_IP6_ADDRESS = "ipv6_address";

    public String name;

    public String[] aliases;

    public String[] links;

    public String ipv4_address;

    public String ipv6_address;

    public boolean useDefaults() {
        return (aliases == null) && (links == null) && (ipv4_address == null)
                && (ipv6_address == null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ServiceNetwork other = (ServiceNetwork) o;

        if (useDefaults() && other.useDefaults()) {
            return true;
        }

        if (name != null ? !name.equals(other.name) : other.name != null) {
            return false;
        }
        if (!Arrays.equals(aliases, other.aliases)) {
            return false;
        }
        if (!Arrays.equals(links, other.links)) {
            return false;
        }
        if (ipv4_address != null ? !ipv4_address.equals(other.ipv4_address)
                : other.ipv4_address != null) {
            return false;
        }
        if (ipv6_address != null ? !ipv6_address.equals(other.ipv6_address)
                : other.ipv6_address != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + Arrays.hashCode(aliases);
        result = prime * result + ((ipv4_address == null) ? 0 : ipv4_address.hashCode());
        result = prime * result + ((ipv6_address == null) ? 0 : ipv6_address.hashCode());
        result = prime * result + Arrays.hashCode(links);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ServiceNetwork {");
        sb.append("name='").append(name != null ? name : "-").append("',");
        sb.append("aliases='");
        if (aliases != null) {
            StringJoiner sj = new StringJoiner(",");
            for (String alias : aliases) {
                sj.add(alias);
            }
            sb.append("{").append(sj.toString()).append("}");
        } else {
            sb.append("-");
        }
        sb.append("',");
        sb.append("links='");
        if (links != null) {
            StringJoiner sj = new StringJoiner(",");
            for (String link : links) {
                sj.add(link);
            }
            sb.append("{").append(sj.toString()).append("}");
        } else {
            sb.append("-");
        }
        sb.append("',");
        sb.append("ipv4_address='").append(ipv4_address != null ? ipv4_address : "-").append("',");
        sb.append("ipv6_address='").append(ipv6_address != null ? ipv6_address : "-").append("'}");
        return sb.toString();
    }

    public static ServiceNetwork fromMap(Map<?,?> map) {
        ServiceNetwork result = new ServiceNetwork();
        if (map.containsKey(FIELD_NAME)) {
            result.name = (String) map.get(FIELD_NAME);
        }
        if (map.containsKey(FIELD_NAME_ALIASES)) {
            result.aliases = (String[]) ((List) map.get(FIELD_NAME_ALIASES)).stream()
                    .toArray(String[]::new);
        }

        if (map.containsKey(FIELD_NAME_IP4_ADDRESS)) {
            result.ipv4_address = (String) map.get(FIELD_NAME_IP4_ADDRESS);
        }

        if (map.containsKey(FIELD_NAME_IP6_ADDRESS)) {
            result.ipv6_address = (String) map.get(FIELD_NAME_IP6_ADDRESS);
        }
        return result;
    }
}
