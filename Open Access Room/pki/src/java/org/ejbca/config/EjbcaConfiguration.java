/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.config;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

/**
 * This file handles configuration from ejbca.properties
 * 
 * @version $Id: EjbcaConfiguration.java 16262 2013-02-04 12:26:05Z anatom $
 */
public final class EjbcaConfiguration {

    private static final Logger log = Logger.getLogger(EjbcaConfiguration.class);

    // This is a singleton with on static methods
    private EjbcaConfiguration() {
    }

    private static final String TRUE = "true";

    /**
     * Check if EJBCA is running in production
     */
    public static boolean getIsInProductionMode() {
        final String value = EjbcaConfigurationHolder.getString("ejbca.productionmode");
        if (TRUE.equalsIgnoreCase(value) || "ca".equalsIgnoreCase(value) || "ocsp".equalsIgnoreCase(value)) {
            return true;
        }
        return false;
    }

    /**
     * Password used to protect XKMS keystores in the database.
     */
    public static String getCaXkmsKeyStorePass() {
        return EjbcaConfigurationHolder.getExpandedString("ca.xkmskeystorepass");
    }

    /**
     * Password used to protect CMS keystores in the database.
     */
    public static String getCaCmsKeyStorePass() {
        return EjbcaConfigurationHolder.getExpandedString("ca.cmskeystorepass");
    }

    /**
     * How long an request should stay valid
     */
    public static long getApprovalDefaultRequestValidity() {
        long value = 28800L;
        try {
            value = Long.parseLong(EjbcaConfigurationHolder.getString("approval.defaultrequestvalidity"));
        } catch (NumberFormatException e) {
            log.warn("\"approval.defaultrequestvalidity\" is not a decimal number. Using default value: " + value);
        }
        return value * 1000L;
    }

    /**
     * How long an approved request should stay valid
     */
    public static long getApprovalDefaultApprovalValidity() {
        long value = 28800L;
        try {
            value = Long.parseLong(EjbcaConfigurationHolder.getString("approval.defaultapprovalvalidity"));
        } catch (NumberFormatException e) {
            log.warn("\"approval.defaultapprovalvalidity\" is not a decimal number. Using default value: " + value);
        }
        return value * 1000L;
    }

    /**
     * Excluded classes from approval.
     */
    public static String getApprovalExcludedClasses() {
        return EjbcaConfigurationHolder.getExpandedString("approval.excludedClasses");
    }

    /**
     * Parameter specifying amount of free memory (Mb) before alarming
     */
    public static long getHealthCheckAmountFreeMem() {
        long value = 1;
        try {
            value = Long.parseLong(EjbcaConfigurationHolder.getString("healthcheck.amountfreemem"));
        } catch (NumberFormatException e) {
            log.warn("\"healthcheck.amountfreemem\" or \"ocsphealthcheck.amountfreemem\" is not a decimal number. Using default value: " + value);
        }
        return value * 1024L * 1024L;
    }

    /**
     * Parameter specifying database test query string. Used to check that the database is operational.
     */
    public static String getHealthCheckDbQuery() {
        return EjbcaConfigurationHolder.getExpandedString("healthcheck.dbquery");
    }

    /**
     * Parameter to specify location of file containing information about maintenance
     */
    public static String getHealthCheckAuthorizedIps() {
        return EjbcaConfigurationHolder.getExpandedString("healthcheck.authorizedips");
    }

    /**
     * Parameter to specify if the check of CA tokens should actually perform a signature test on the CA token.
     */
    public static boolean getHealthCheckCaTokenSignTest() {
        return TRUE.equalsIgnoreCase(EjbcaConfigurationHolder.getString("healthcheck.catokensigntest"));
    }

    /**
     * Parameter to specify if a connection test of publishers should be performed.
     */
    public static boolean getHealthCheckPublisherConnections() {
        return TRUE.equalsIgnoreCase(EjbcaConfigurationHolder.getString("healthcheck.publisherconnections"));
    }

    /**
     * Parameter to specify location of file containing information about maintenance
     */
    public static String getHealthCheckMaintenanceFile() {
        return EjbcaConfigurationHolder.getExpandedString("healthcheck.maintenancefile");
    }

    /**
     * Parameter to configure name of maintenance property.
     */
    public static String getHealthCheckMaintenancePropertyName() {
        return EjbcaConfigurationHolder.getExpandedString("healthcheck.maintenancepropertyname");
    }

    /**
     * @return Text string used to say that every thing is ok with this node.
     */
    public static String getOkMessage() {
        return EjbcaConfigurationHolder.getExpandedString("healthcheck.okmessage");
    }
    
    /**
     * 
     * @return true if an error code 500 should be sent in case of error.
     */
    public static boolean getSendServerError() {
     return TRUE.equalsIgnoreCase(EjbcaConfigurationHolder.getExpandedString("healthcheck.sendservererror"));
    }
    
    /**
     * 
     * @return a static error message instead of one generated by the HealthChecker
     */
    public static String getCustomErrorMessage() {
        return EjbcaConfigurationHolder.getExpandedString("healthcheck.customerrormessage");
    }

    /**
     * Class performing the healthcheck. Must implement the IHealthCheck interface.
     */
    public static String getHealthCheckClassPath() {
        return EjbcaConfigurationHolder.getExpandedString("healthcheck.classpath");
    }

    /**
     * Parameter to specify if retrieving endEntity profiles in EndEntityProfileSessionBean should be cached, and in that case for how long.
     */
    public static long getCacheEndEntityProfileTime() {
        long time = 1000; // cache 1 second is the default
        try {
            time = Long.valueOf(EjbcaConfigurationHolder.getString("eeprofiles.cachetime"));
        } catch (NumberFormatException e) {
            log.error("Invalid value in eeprofiles.cachetime, must be decimal number (milliseconds to cache EndEntity profiles): " + e.getMessage());
        }
        return time;
    }
    
    /**
     * Parameter to specify if retrieving Publishers from PublisherSession should be cached, and in that case for how long.
     */
    public static long getCachePublisherTime() {
        final String value = EjbcaConfigurationHolder.getString("publisher.cachetime");
        long time = 1000; // cache 1 second is the default
        try {
            if (value!=null) {
                time = Long.valueOf(value);
            }
        } catch (NumberFormatException e) {
            log.error("Invalid value in publisher.cachetime, must be decimal number (milliseconds to cache Publisher): " + e.getMessage());
        }
        return time;
    }

    /**
     * Parameter to specify if retrieving GlobalConfiguration (in GlobalConfigurationSessionBean) should be cached, and in that case for how long.
     */
    public static long getCacheGlobalConfigurationTime() {
        long time = 30000; // cache 30 seconds is the default
        try {
            time = Long.valueOf(EjbcaConfigurationHolder.getString("globalconfiguration.cachetime"));
        } catch (NumberFormatException e) {
            log.error("Invalid value in globalconfiguration.cachetime, must be decimal number (milliseconds to cache global configuration): "
                    + e.getMessage());
        }
        return time;
    }

    /** Custom Available Access Rules. */
    public static String[] getCustomAvailableAccessRules() {
    	return StringUtils.split(EjbcaConfigurationHolder.getString("ejbca.customavailableaccessrules"), ';');
    }

    /**
     * Parameter to specify how to treat data in the database, when running in a clustered environment with different EJBCA versions.
     */
    public static int getEffectiveApplicationVersion() {
        final String readVersion = EjbcaConfigurationHolder.getString("app.version.effective");
        if (readVersion.startsWith("3.11")) {
            return 311;
        }
        return 400;
    }

    /**
     * Parameter to specify if how many rounds the BCrypt algorithm should process passwords stored in the database.
     * 0 means use the old way instead of BCrypt.
     */
    public static int getPasswordLogRounds() {
    	final String PROPERTY_NAME = "ejbca.passwordlogrounds";
        int time = 1; // only 1 single round is the default
        try {
            time = Integer.valueOf(EjbcaConfigurationHolder.getString(PROPERTY_NAME));
        } catch (NumberFormatException e) {
            log.error("Invalid value in " + PROPERTY_NAME + ", must be decimal number, using 1 round: " + e.getMessage());
        }
        return time;
    }
    
    public static String getCliDefaultUser() {
        return EjbcaConfigurationHolder.getString("ejbca.cli.defaultusername");
    }
    
    public static String getCliDefaultPassword() {
        return EjbcaConfigurationHolder.getString("ejbca.cli.defaultpassword");
    }

    public static String getScepDefaultCA() {
        return EjbcaConfigurationHolder.getString("scep.defaultca");
    }

}
