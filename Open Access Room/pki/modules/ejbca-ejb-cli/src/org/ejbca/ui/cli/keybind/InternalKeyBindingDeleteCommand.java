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
package org.ejbca.ui.cli.keybind;

import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.keybind.InternalKeyBindingMgmtSessionRemote;

/**
 * See getDescription().
 * 
 * @version $Id: InternalKeyBindingDeleteCommand.java 17604 2013-09-18 12:13:45Z jeklund $
 */
public class InternalKeyBindingDeleteCommand extends BaseInternalKeyBindingCommand {

    @Override
    public String getSubCommand() {
        return "delete";
    }

    @Override
    public String getDescription() {
        return "Deletes the specified InternalKeyBinding.";
    }

    @Override
    public void executeCommand(Integer internalKeyBindinId, String[] args) throws AuthorizationDeniedException {
        if (args.length < 2) {
            getLogger().info("Description: " + getDescription());
            getLogger().info("Usage: " + getCommand() + " <name>");
            return;
        }
        final InternalKeyBindingMgmtSessionRemote internalKeyBindingMgmtSession = ejb.getRemoteSession(InternalKeyBindingMgmtSessionRemote.class);
        if (internalKeyBindingMgmtSession.deleteInternalKeyBinding(getAdmin(), internalKeyBindinId)) {
            getLogger().info("InternalKeyBinding with id " + internalKeyBindinId + " was successfully removed.");
        } else {
            getLogger().info("InternalKeyBinding with id " + internalKeyBindinId + " could not be removed.");
        }
    }
}
