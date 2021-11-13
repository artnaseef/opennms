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

import java.util.Hashtable;
import java.util.Optional;

import org.apache.felix.cm.PersistenceManager;
import org.apache.felix.cm.file.FilePersistenceManager;
import org.opennms.features.config.service.api.ConfigurationManagerService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {

    private final static Logger LOG = LoggerFactory.getLogger(Activator.class);

    // TODO: Patrick we need to register for all OSGI PIDs
    @Deprecated
    private final static String PID = "org.opennms.features.topology.app.icons.application";

    private ServiceRegistration<PersistenceManager> registration;

    @Override
    public void start(BundleContext context) throws Exception {
        Hashtable<String, Object> config = new Hashtable<>();
        config.put("name", CmPersistenceManager.class.getName());
        config.put("service.ranking", 1000);
        LOG.info("Registering service {}.", CmPersistenceManager.class.getSimpleName());

        final ConfigurationManagerService configService = Optional.ofNullable(context.getServiceReference(ConfigurationManagerService.class))
                .map(context::getService)
                .orElseThrow(() -> new IllegalStateException("Cannot find " + ConfigurationManagerService.class.getName()));

        final PersistenceManager delegate =
                context.getServiceReferences(PersistenceManager.class, null)
                        .stream()
                        .map(context::getService)
                        .filter(s -> s instanceof FilePersistenceManager)
                        .findAny()
                        .orElseThrow(() -> new IllegalStateException("Cannot find " + PersistenceManager.class.getName()));

        CmPersistenceManager persistenceManager = new CmPersistenceManager(context, configService, delegate);
        registration = context.registerService(PersistenceManager.class, persistenceManager, config);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (registration != null) {
            registration.unregister();
        }
        LOG.info(CmPersistenceManager.class.getSimpleName() + " stopped");
    }
}