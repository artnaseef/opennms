package org.opennms.netmgt.threshd;

import java.io.File;
import java.util.Map;

import org.apache.log4j.Category;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.config.ThresholdingConfigFactory;
import org.opennms.netmgt.poller.NetworkInterface;
import org.opennms.netmgt.utils.ParameterMap;

public class SnmpThresholdConfiguration {
    
    /**
     * Default thresholding interval (in milliseconds).
     * 
     */
    private static final int DEFAULT_INTERVAL = 300000; // 300s or 5m
    
    /**
     * Default age before which a data point is considered "out of date"
     */
    
    private static final int DEFAULT_RANGE = 0; 

    private static final String THRESHD_SERVICE_CONFIG_KEY = SnmpThresholdConfiguration.class.getName();


    public static SnmpThresholdConfiguration get(NetworkInterface iface, Map parms) {
        SnmpThresholdConfiguration config = (SnmpThresholdConfiguration)iface.getAttribute(THRESHD_SERVICE_CONFIG_KEY);
        if (config == null) {
            config = new SnmpThresholdConfiguration(parms);
            iface.setAttribute(THRESHD_SERVICE_CONFIG_KEY, config);
        }
        return config;
    }

    private Map m_parms;
    
    private ThresholdGroup m_thresholdGroup;

    private ThresholdResourceType m_nodeResourceType;

    private ThresholdResourceType m_ifResourceType;
    
    private SnmpThresholdConfiguration(Map parms) {
        m_parms = parms;
        m_thresholdGroup = new ThresholdGroup(ParameterMap.getKeyedString(m_parms, "thresholding-group", "default"));
        
        setNodeResourceType(new ThresholdResourceType("node", m_thresholdGroup));
        setIfResourceType(new ThresholdResourceType("if", m_thresholdGroup));
    }
    
    File getRrdRepository() {
        return getGroup().getRrdRepository();
    }

    private Category log() {
        return ThreadCategory.getInstance(getClass());
    }
    
    public ThresholdGroup getGroup() {
    	return m_thresholdGroup;
    }

    public String getGroupName() {
        return getGroup().getName();
    }

    public int getRange() {
        return ParameterMap.getKeyedInteger(m_parms, "range", SnmpThresholdConfiguration.DEFAULT_RANGE);
    }

    public int getInterval() {
        return ParameterMap.getKeyedInteger(m_parms, "interval", SnmpThresholdConfiguration.DEFAULT_INTERVAL);
    }

    private void setNodeResourceType(ThresholdResourceType nodeResourceType) {
        m_nodeResourceType = nodeResourceType;
    }

    public ThresholdResourceType getNodeResourceType() {
        return m_nodeResourceType;
    }

    private void setIfResourceType(ThresholdResourceType ifResourceType) {
        m_ifResourceType = ifResourceType;
    }

    public ThresholdResourceType getIfResourceType() {
        return m_ifResourceType;
    }

}
