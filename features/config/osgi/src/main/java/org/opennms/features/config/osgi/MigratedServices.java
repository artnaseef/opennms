package org.opennms.features.config.osgi;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

// TODO: Patrick: find a better solution, e.g. read all available PIDs dynamically from CM.
public class MigratedServices {
    private final static ImmutableSet<Object> PIDS = ImmutableSet.builder()
            .add("org.opennms.features.datachoices")
            .build();

    public static boolean isMigrated(final String pid) {
        return PIDS.contains(pid);
    }
}
