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

import org.ejbca.core.ejb.upgrade.UpgradeSessionRemote;



/**
 * Implements call to the upgrade function
 * 
 * @version $Id: Upgrade.java 17764 2013-10-09 13:01:40Z mikekushner $
 */
public class Upgrade extends BaseCommand {

    public String getMainCommand() {
        return null;
    }

    public String getSubCommand() {
        return "upgrade";
    }

    public String getDescription() {
        return "(Use 'ant upgrade' instead of running this directly)";
    }

    public void execute(String[] args) throws ErrorAdminCommandException {
             
        if (args.length < 3) {
            getLogger().error("Insufficient information to perform upgrade.");
            return;
        }
        final String database = args[1];
        final String upgradeFromVersion = args[2];
        final boolean isPost = args.length > 3;
        getLogger().debug(args[0] + " ejbcaDB='" + database + "' ejbcaUpgradeFromVersion='" + upgradeFromVersion + "' isPost='" + isPost + "'");
        // Check pre-requisites
        /*if (!appServerRunning()) {
            getLogger().error("The application server must be running.");
            return;
        }*/
        // Upgrade the database

        final boolean ret = ejb.getRemoteSession(UpgradeSessionRemote.class).upgrade(database, upgradeFromVersion, isPost);
        if (ret) {
            getLogger().info("Upgrade completed.");
        } else {
            getLogger().error("Upgrade not performed, see server log for details.");
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
