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

package org.ejbca.ui.cli;

import java.util.List;

import org.cesecore.authorization.control.AccessControlSessionRemote;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionRemote;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.config.Configuration;
import org.ejbca.core.ejb.config.GlobalConfigurationSessionRemote;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionRemote;
import org.ejbca.util.CliTools;


/**
 * Clears caches used internally by EJBCA. The caches are used to limit the number of database queries issued to the database.
 * See conf/cache.properties.sample for configuration of caches.
 *
 * @version $Id: ClearCacheCommand.java 17764 2013-10-09 13:01:40Z mikekushner $
 */
public class ClearCacheCommand extends BaseCommand {

	public String getMainCommand() { return null; }
	public String getSubCommand() { return "clearcache"; }
	public String getDescription() { return "Clears caches used internally by EJBCA."; }

	public void execute(final String[] args) throws ErrorAdminCommandException {
        if (args.length < 2) {
        	getLogger().info("Description: " + getDescription());
        	getLogger().info("See conf/cache.properties.sample for config options. This command should only be needed if cache times are set yo very high values.");
            getLogger().info("Usage: " + getCommand() + " -all -globalconf -eeprofile -certprofile -authorization -ca");
            getLogger().info("Where arguments are optional, but you have to provide at least one");
        	return;
        }		

		// Get and remove switches
		final List<String> argsList = CliTools.getAsModifyableList(args);
		final boolean all = argsList.remove("-all");
		final boolean globalconf = argsList.remove("-globalconf") || all;
		final boolean eeprofile = argsList.remove("-eeprofile") || all;
		final boolean certprofile = argsList.remove("-certprofile") || all;
		final boolean authorization = argsList.remove("-authorization") || all;
		final boolean cacache = argsList.remove("-ca") || all;

		try {
			if (globalconf) {
				getLogger().info("Flushing global configuration cache.");
				// Flush GlobalConfiguration
				ejb.getRemoteSession(GlobalConfigurationSessionRemote.class).flushConfigurationCache(Configuration.GlobalConfigID);
				
                getLogger().info("Flushing CMP configuration cache.");
                // Flush CMPConfiguration
                ejb.getRemoteSession(GlobalConfigurationSessionRemote.class).flushConfigurationCache(Configuration.CMPConfigID);
			}
			if (eeprofile) {
				getLogger().info("Flushing end entity profile cache.");
				// Flush End Entity profiles
				ejb.getRemoteSession(EndEntityProfileSessionRemote.class).flushProfileCache();
			}
			if (certprofile) {
				getLogger().info("Flushing certificate profile cache.");
				// Flush Certificate profiles
				ejb.getRemoteSession(CertificateProfileSessionRemote.class).flushProfileCache();
			}
			if (authorization) {
				getLogger().info("Flushing authorization cache.");
				// Flush access control
				ejb.getRemoteSession(AccessControlSessionRemote.class).forceCacheExpire();
			}
			if (cacache) {
				getLogger().info("Flushing CA cache.");
				// Flush CAs
				EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
			}
		} catch (Exception e) {
			throw new ErrorAdminCommandException(e);
		}
	}
    @Override
    public String[] getMainCommandAliases() {
        return new String[]{};
    }
    
    @Override
    public String[] getSubCommandAliases() {
        return new String[]{};
    }
}
