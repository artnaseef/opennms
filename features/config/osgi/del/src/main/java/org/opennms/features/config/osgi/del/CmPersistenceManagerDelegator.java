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

package org.opennms.features.config.osgi.del;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.felix.cm.PersistenceManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;

/**
 * Our own implementation of a PersistenceManager.
 * It delegates to ConfigurationManagerService for OpenNMS bundles
 * It delegates to FilePersistenceManager all other bundles
 * Must be activated in custom.properties: felix.cm.pm=org.opennms.config.osgi.del.CmPersistenceManagerDelegator
 */
public class CmPersistenceManagerDelegator implements PersistenceManager {

    private final BundleContext context;
    private final PersistenceManager fileDelegate;
    private PersistenceManager cmDelegate;
    private final AtomicBoolean cmRegistered = new AtomicBoolean(false);

    public CmPersistenceManagerDelegator(final BundleContext context) {
        this.context = context;
        this.fileDelegate = findPersistenceManager("org.apache.felix.cm.file.FilePersistenceManager");
    }

    private PersistenceManager findPersistenceManager(String className) {
        try {
            return context.getServiceReferences(PersistenceManager.class, null)
                    .stream()
                    .map(context::getService)
                    .filter(s -> className.equals(s.getClass().getName()))
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException("Cannot find " + className));
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean exists(final String pid) {
        return getDelegate(pid).exists(pid);
    }

    @Override
    public Enumeration getDictionaries() throws IOException {
        List<Dictionary<String, String>> dictionaries = new ArrayList<>();
        dictionaries.addAll(Collections.list(fileDelegate.getDictionaries()));
        if (this.cmRegistered.get()) {
            dictionaries.addAll(Collections.list(cmDelegate.getDictionaries()));
        }
        return Collections.enumeration(dictionaries);
    }

    @Override
    public Dictionary load(String pid) throws IOException {
        return getDelegate(pid).load(pid);
    }

    @Override
    public void store(String pid, Dictionary props) throws IOException {
        getDelegate(pid).store(pid, props);
    }

    @Override
    public void delete(final String pid) throws IOException {
        getDelegate(pid).delete(pid);
    }

    /**
     * Returns either the native FilePersistenceManger or CmPersistenceManager depending on the pid.
     */
    private PersistenceManager getDelegate(final String pid) {
        if (MigratedServices.isMigrated(pid)) {
            ensureCmIsAvailable();
            return this.cmDelegate;
        } else {
            return this.fileDelegate;
        }
    }

    private void ensureCmIsAvailable() {
        if (cmRegistered.get()) {
            return; // registration already done
        }
        // TODO: Patrick: do we need to ensure here against concurrency? (are bundles loaded in parallel?)
        if (cmRegistered.compareAndSet(false, true)) {
            this.cmDelegate = findPersistenceManager("org.opennms.features.config.osgi.CmPersistenceManager");
        }
    }
}