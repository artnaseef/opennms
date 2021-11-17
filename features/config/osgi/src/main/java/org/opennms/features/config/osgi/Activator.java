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
import java.util.Hashtable;
import java.util.Optional;

import org.apache.felix.cm.PersistenceManager;
import org.apache.felix.cm.file.FilePersistenceManager;
import org.opennms.features.config.service.api.ConfigKey;
import org.opennms.features.config.service.api.ConfigurationManagerService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {

    private final static Logger LOG = LoggerFactory.getLogger(Activator.class);
    private ServiceRegistration<PersistenceManager> registration;

    @Override
    public void start(BundleContext context) throws Exception {
        Hashtable<String, Object> config = new Hashtable<>();
        config.put("name", CmPersistenceManager.class.getName());
        config.put("service.ranking", 1000);
        LOG.info("Registering service {}.", CmPersistenceManager.class.getSimpleName());

        // find cm
        final ConfigurationManagerService cm = Optional.ofNullable(context.getServiceReference(ConfigurationManagerService.class))
                .map(context::getService)
                .orElseThrow(() -> new IllegalStateException("Cannot find " + ConfigurationManagerService.class.getName()));

        // find the FilePersistenceManager, we delegate to it for non converted services
        final PersistenceManager delegate =
                context.getServiceReferences(PersistenceManager.class, null)
                        .stream()
                        .map(context::getService)
                        .filter(s -> s instanceof FilePersistenceManager)
                        .findAny()
                        .orElseThrow(() -> new IllegalStateException("Cannot find " + PersistenceManager.class.getName()));

        // Create Runnable to register CallbacksForConfigChanges:
        // We need to run this Runnable after the full registration of the CMPersistenceManager (after Activator.start() is fully finished)
        // otherwise ConfigAdmin might not find CmPersistenceManager
        // thus we execute the registration the first time a method on CmPersistenceManager is called => at that time it is fully registered
        Runnable registerCallbacksForConfigChanges = () -> registerCallbacksForConfigChanges(context, cm);
        CmPersistenceManager persistenceManager = new CmPersistenceManager(cm, delegate, registerCallbacksForConfigChanges);

        // register our CmPersistenceManager (instead of FilePersistenceManager)
        registration = context.registerService(PersistenceManager.class, persistenceManager, config);

        LOG.info(CmPersistenceManager.class.getSimpleName() + " started");
    }

    private void registerCallbacksForConfigChanges(BundleContext context, ConfigurationManagerService cm)  {
        // Find ConfigurationAdmin;
        final ConfigurationAdmin configurationAdmin;
        try {
            configurationAdmin = context.getServiceReferences(ConfigurationAdmin.class, null)
                    .stream()
                    .map(context::getService)
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException("Cannot find " + PersistenceManager.class.getName()));
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException(e);
        }

        // Register all callbacks for config changes
        for (String pid : MigratedServices.PIDS) {
            ConfigKey key = new ConfigKey(pid, "default");
            cm.registerReloadConsumer(key, k -> {
                try {
                    configurationAdmin.getConfiguration(k.getConfigName()).update();
                } catch (IOException e) {
                    LOG.warn("Cannot register callback from pid={}", pid, e);
                }
            });
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (registration != null) {
            registration.unregister();
        }
        LOG.info(CmPersistenceManager.class.getSimpleName() + " stopped");
    }
}