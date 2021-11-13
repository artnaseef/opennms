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

import org.apache.felix.cm.PersistenceManager;
import org.opennms.features.config.service.api.ConfigurationManagerService;
import org.opennms.features.config.service.api.JsonAsString;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Our own implementation of a PersistenceManager (subclass of FilePersistenceManager).
 * Must be activated in custom.properties: felix.cm.pm=org.opennms.config.osgi.CmPersistenceManager
 */
public class CmPersistenceManager implements PersistenceManager { // TODO: Patrick decide if we need NotCachablePersistenceManager

    private final static Logger LOG = LoggerFactory.getLogger(CmPersistenceManager.class);

    // TODO: Patrick we need to register for all OSGI PIDs
    private final static String PID = "org.opennms.features.topology.app.icons.application";
    private final static String CONFIG_ID = "default"; // TODO: Patrick deal with services with multiple configurations

    private final ConfigurationManagerService configService;
    private final BundleContext bundleContext;
    private final PersistenceManager delegate;


    public CmPersistenceManager(final BundleContext bundleContext, final ConfigurationManagerService configService, final PersistenceManager delegate) {
        this.configService = configService;
        // this.configService.registerForUpdates(PID, this);
        this.bundleContext = bundleContext;
        this.delegate = delegate;
    }

    @Override
    public boolean exists(final String pid) {
        if (shouldDelegate(pid)) {
            return delegate.exists(pid);
        }
        try {
            return configService.getJSONConfiguration(pid, "default").isPresent();
        } catch (IOException e) {
           return false;
        }
    }

    @Override
    public Enumeration getDictionaries() throws IOException {
        List<Dictionary<String, String>> dictionaries = Collections.list(delegate.getDictionaries());
        loadInternal(PID).ifPresent(dictionaries::add);
        return Collections.enumeration(dictionaries);
    }

    @Override
    public Dictionary load(String pid) throws IOException {
        if (shouldDelegate(pid)) {
            return delegate.load(pid); // nothing to do for us
        }
        return loadInternal(pid)
                .orElse(new Hashtable());
    }

    private Optional<Dictionary<String, String>> loadInternal(String pid) throws IOException {
        return configService.getJSONConfiguration(pid, "default")
                .map(DictionaryUtil::createFromJson);
    }

    @Override
    public void store(String pid, Dictionary props) throws IOException {
        if (shouldDelegate(pid)) {
            delegate.store(pid, props);
            return; // nothing to do for us
        }

        Optional<Dictionary<String, String>> confFromConfigService = loadInternal(pid);
        if(confFromConfigService.isEmpty() || !equalsWithoutRevision(props, confFromConfigService.get())) {
            configService.updateConfiguration(pid, "default", new JsonAsString(DictionaryUtil.writeToJson(props)));
        }
    }

    @Override
    public void delete(final String pid) throws IOException {
        if (shouldDelegate(pid)) {
            delegate.delete(pid);
            return; // nothing to do for us
        }
        LOG.warn("Deletion is not supported. Will ignore it for pid={}", pid);
    }

    private boolean shouldDelegate(final String pid) {
        return !MigratedServices.isMigrated(pid);
    }

    // Hook to be notified of changes from ConfigurationService
// TODO: Patrick
    public void configurationHasChanged(final String pid) {

        Optional<Dictionary<String, String>> dictionary = null ; // TODO: Patrick configService.getConfigurationAsDictionary(pid);
        if (dictionary.isEmpty()) {
            // something is wrong here. We should get it since we were notified about the change
            return;
        }
        Dictionary<String, String> confFromConfigService = dictionary.get();

        final Optional<ConfigurationAdmin> configAdminOpt = Optional.ofNullable(bundleContext.getServiceReference(ConfigurationAdmin.class))
                .map(bundleContext::getService);
        if(configAdminOpt.isEmpty()) {
            // something is wrong here. We should get the ConfigurationAdmin but we didn't => do nothing.
            return;
        }


        Optional<Configuration> configOpt = configAdminOpt.map(a -> silenceToNull(() -> a.getConfiguration(pid, "?org.opennms")));
        if(configOpt.isEmpty()) {
            // something is wrong here. We should get the Configuration but we didn't => do nothing.
            return;
        }

        Configuration config = configOpt.get();
        Dictionary<String, Object> confFromAdminService = Optional.ofNullable(config.getProperties())
                .orElse(new Hashtable<>());

        // remove properties that are no longer present
        Enumeration<String> keys = confFromAdminService.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (null == confFromConfigService.get(key)) {
                confFromAdminService.remove(key);
            }
        }

        // set / update properties
        keys = confFromConfigService.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            confFromAdminService.put(key, confFromConfigService.get(key));
        }
        try {
            config.update(confFromAdminService);
        } catch (IOException e) {
            // TODO: Patrick
        }
    }

    // TODO: Patrick: replace or move everything below to helper class


    private static <Output> Output silenceToNull(final ProducerWithException<Output> function) {
        try {
            return function.apply();
        } catch (Exception e) {
            return null;
        }
    }

    @FunctionalInterface
    public interface ProducerWithException<Output> {
        Output apply() throws Exception;
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