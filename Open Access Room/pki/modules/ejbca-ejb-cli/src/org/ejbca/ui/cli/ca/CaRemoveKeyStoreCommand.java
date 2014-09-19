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

package org.ejbca.ui.cli.ca;

import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.ui.cli.CliUsernameException;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Remove the CA token keystore from a CA.
 * 
 * @version $Id: CaRemoveKeyStoreCommand.java 17764 2013-10-09 13:01:40Z mikekushner $
 */
public class CaRemoveKeyStoreCommand extends BaseCaAdminCommand {

    @Override
    public String getSubCommand() { return "removekeystore"; }
    @Override
    public String getDescription() { return "Remove the CA token keystore from a CA"; }

    @Override
    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            args = parseUsernameAndPasswordFromArgs(args);
        } catch (CliUsernameException e) {
            return;
        }
        
        
		if (args.length < 2) {
    		getLogger().info("Description: " + getDescription());
    		getLogger().info("Usage: " + getCommand() + " <CA name>");
			return;
		}
		try {
			String caName = args[1];
			ejb.getRemoteSession(CAAdminSessionRemote.class).removeCAKeyStore(getAuthenticationToken(cliUserName, cliPassword), caName);
		} catch (Exception e) {
			throw new ErrorAdminCommandException(e);
		}
	}
}
