/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2021 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2021 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.flows.api;

import org.opennms.netmgt.telemetry.config.model.PackageConfig;

public class ProcessingOptions {

    public final boolean enableAggregation;
    public final boolean enableAggregationPersistence;

    public final PackageConfig packageConfig;

    private ProcessingOptions(final Builder builder) {
        this.enableAggregation = builder.enableAggregation;
        this.enableAggregationPersistence = builder.enableAggregationPersistence;
        this.packageConfig = builder.packageConfig;
    }

    public static class Builder {
        private boolean enableAggregation;
        private boolean enableAggregationPersistence;

        private PackageConfig packageConfig;

        private Builder() {}

        public Builder setEnableAggregation(final boolean enableAggregation) {
            this.enableAggregation = enableAggregation;
            return this;
        }

        public Builder setEnableAggregationPersistence(final boolean enableAggregationPersistence) {
            this.enableAggregationPersistence = enableAggregationPersistence;
            return this;
        }

        public Builder setPackageConfig(final PackageConfig packageConfig) {
            this.packageConfig = packageConfig;
            return this;
        }

        public ProcessingOptions build() {
            return new ProcessingOptions(this);
        }
    }


    public static Builder builder() {
        return new Builder();
    }
}
