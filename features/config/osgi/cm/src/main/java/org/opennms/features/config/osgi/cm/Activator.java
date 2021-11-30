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

package org.opennms.features.config.osgi.cm;

import static org.opennms.features.config.osgi.cm.LogUtil.logInfo;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Optional;

import org.apache.felix.cm.PersistenceManager;
import org.opennms.features.config.service.api.ConfigKey;
import org.opennms.features.config.service.api.ConfigurationManagerService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {

    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

    private ServiceRegistration<PersistenceManager> registration;

    @Override
    public void start(BundleContext context) throws Exception {

        // Register CmPersistenceManager
        Hashtable<String, Object> config = new Hashtable<>();
        config.put("name", CmPersistenceManager.class.getName());
        LOG.info( "Registering service {}.", CmPersistenceManager.class.getSimpleName() );
        final ConfigurationManagerService cm = findService(context, ConfigurationManagerService.class);
        CmPersistenceManager persistenceManager = new CmPersistenceManager(cm);
        registration = context.registerService(PersistenceManager.class, persistenceManager, config);

        final ConfigurationAdmin configurationAdmin = findService(context, ConfigurationAdmin.class);
        // primeConfigurationAdmin(configurationAdmin, persistenceManager);
        registerCallbacks(context, cm, persistenceManager);

        logInfo("{0} started.", CmPersistenceManager.class.getSimpleName());
    }

    /**
     * We are started late (after org.osgi.service.cm.ConfigurationAdmin) in the startup sequence.
     * Therefore, ConfigurationAdmin (which caches configs) doesn't know about our configs.
     * Let's tell him.
     */
    private void primeConfigurationAdmin(ConfigurationAdmin configurationAdmin, PersistenceManager persistenceManager) {
        for (String pid : MigratedServices.PIDS) {
            updateConfig(configurationAdmin, persistenceManager, pid);
        }
    }

    private void registerCallbacks(BundleContext context, ConfigurationManagerService cm, PersistenceManager persistenceManager) {
        // Register callbacks
        final ConfigurationAdmin configurationAdmin = findService(context, ConfigurationAdmin.class);
        for (String pid : MigratedServices.PIDS) {
            ConfigKey key = new ConfigKey(pid, "default");
            cm.registerReloadConsumer(key, k -> updateConfig(configurationAdmin, persistenceManager, pid));
        }
    }

    private void updateConfig(ConfigurationAdmin configurationAdmin, PersistenceManager persistenceManager, String pid) {
        try {
            Dictionary fromCM = persistenceManager.load(pid);
            configurationAdmin
                    .getConfiguration(pid)
                    .update();
        // .update(fromCM);
                    // .updateIfDifferent(fromCM); // TODO: Patrick: this doesn't seem to work!
        } catch (IOException e) {
            logInfo("Cannot load configuration for pid=" + pid, e );
        }
    }

    private <T> T findService(BundleContext context, Class<T> clazz) {
        return Optional.ofNullable(context.getServiceReference(clazz))
                .map(context::getService)
                .orElseThrow(() -> new IllegalStateException("Cannot find " + clazz.getName()));
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (registration != null) {
            registration.unregister();
        }
        logInfo( "{0} stopped.", CmPersistenceManager.class.getSimpleName());
    }
}