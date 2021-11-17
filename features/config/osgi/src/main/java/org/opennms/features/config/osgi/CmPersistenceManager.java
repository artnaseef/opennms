/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2021 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 2021-2021 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.config.osgi;

import java.io.IOException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.felix.cm.PersistenceManager;
import org.opennms.features.config.service.api.ConfigKey;
import org.opennms.features.config.service.api.ConfigurationManagerService;
import org.opennms.features.config.service.api.JsonAsString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Our own implementation of a PersistenceManager (subclass of FilePersistenceManager).
 * Must be activated in custom.properties: felix.cm.pm=org.opennms.config.osgi.CmPersistenceManager
 */
public class CmPersistenceManager implements PersistenceManager {

    private final static Logger LOG = LoggerFactory.getLogger(CmPersistenceManager.class);

    private final static String CONFIG_ID = "default"; // TODO: Patrick deal with services with multiple configurations

    private final ConfigurationManagerService configService;
    private final PersistenceManager delegate;
    private final Runnable registerCallbacksForConfigChanges;
    private final AtomicBoolean callbacksRegistered = new AtomicBoolean(false);

    public CmPersistenceManager(final ConfigurationManagerService configService,
                                final PersistenceManager delegate,
                                final Runnable registerCallbacksForConfigChanges) {
        this.configService = configService;
        this.delegate = delegate;
        this.registerCallbacksForConfigChanges = registerCallbacksForConfigChanges;
    }

    @Override
    public boolean exists(final String pid) {
        ensureCallbacksHaveBeenRegistered();
        if (shouldDelegate(pid)) {
            return delegate.exists(pid);
        }
        return configService.getJSONConfiguration(pid, CONFIG_ID).isPresent();
    }

    @Override
    public Enumeration getDictionaries() throws IOException {
        ensureCallbacksHaveBeenRegistered();
        List<Dictionary<String, String>> dictionaries = Collections.list(delegate.getDictionaries());
        for(String pid : MigratedServices.PIDS) {
            loadInternal(pid).ifPresent(dictionaries::add);
        }
        return Collections.enumeration(dictionaries);
    }

    @Override
    public Dictionary load(String pid) throws IOException {
        ensureCallbacksHaveBeenRegistered();
        if (shouldDelegate(pid)) {
            return delegate.load(pid); // nothing to do for us
        }
        return loadInternal(pid)
                .orElse(new Hashtable());
    }

    private Optional<Dictionary<String, String>> loadInternal(String pid) throws IOException {
        return configService.getJSONConfiguration(pid, CONFIG_ID)
                .map(DictionaryUtil::createFromJson);
    }

    @Override
    public void store(String pid, Dictionary props) throws IOException {
        ensureCallbacksHaveBeenRegistered();
        if (shouldDelegate(pid)) {
            delegate.store(pid, props);
            return; // nothing to do for us
        }

        Optional<Dictionary<String, String>> confFromConfigService = loadInternal(pid);
        if(confFromConfigService.isEmpty() || !equalsWithoutRevision(props, confFromConfigService.get())) {
            configService.updateConfiguration(new ConfigKey(pid, CONFIG_ID), new JsonAsString(DictionaryUtil.writeToJson(props)));
        }
    }

    @Override
    public void delete(final String pid) throws IOException {
        ensureCallbacksHaveBeenRegistered();
        if (shouldDelegate(pid)) {
            delegate.delete(pid);
            return; // nothing to do for us
        }
        LOG.warn("Deletion is not supported. Will ignore it for pid={}", pid);
    }

    private boolean shouldDelegate(final String pid) {
        return !MigratedServices.isMigrated(pid);
    }

    private void ensureCallbacksHaveBeenRegistered() {
        if (callbacksRegistered.get()) {
            return; // already done
        }
        if (callbacksRegistered.compareAndSet(false, true)) {
            registerCallbacksForConfigChanges.run();
        }
    }

    public static boolean equalsWithoutRevision(Dictionary<String, String> a, Dictionary<String, String> b) {
        if (a == null && b == null) {
            return true;
        } else if (a == null || b == null) {
            return false;
        } else if (a.size() != b.size()) {
            return false;
        }

        return Collections.list(a.keys())
                .stream()
                .filter(key -> !":org.apache.felix.configadmin.revision:".equals(key))
                .allMatch(key -> a.get(key).equals(b.get(key)));
    }
}