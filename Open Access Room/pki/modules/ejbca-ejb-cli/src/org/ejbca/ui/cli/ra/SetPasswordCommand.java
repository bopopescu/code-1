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
 
package org.ejbca.ui.cli.ra;

import javax.ejb.FinderException;

import org.cesecore.authorization.AuthorizationDeniedException;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionRemote;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.ejbca.ui.cli.CliUsernameException;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Set the (hashed) password for an end entity in the database.
 *
 * @version $Id: SetPasswordCommand.java 17764 2013-10-09 13:01:40Z mikekushner $
 */
public class SetPasswordCommand extends BaseRaCommand {

	@Override
	public String getSubCommand() { return "setpwd"; }
	
	@Override
	public String getDescription() { return "Set a (hashed) password for an end entity"; }

    @Override
    public String[] getSubCommandAliases() {
        return new String[]{};
    }
	
	@Override
    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            args = parseUsernameAndPasswordFromArgs(args);
        } catch (CliUsernameException e) {
            return;
        }
        
        try {
            if (args.length < 3) {
    			getLogger().info("Description: " + getDescription());
            	getLogger().info("Usage: " + getCommand() + " <username> <password>");
                return;
            }
            String username = args[1];
            String password = args[2];
            getLogger().info("Setting password (hashed only) " + password + " for user " + username);
            try {
                ejb.getRemoteSession(EndEntityManagementSessionRemote.class).setPassword(getAuthenticationToken(cliUserName, cliPassword), username, password);
            } catch (AuthorizationDeniedException e) {
            	getLogger().error("Not authorized to change userdata.");
            } catch (UserDoesntFullfillEndEntityProfile e) {
            	getLogger().error("Given end entity doesn't fullfill profile.");
            } catch (FinderException e) {
            	getLogger().error("End entity with username '"+username+"' does not exist.");
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
    }
}
