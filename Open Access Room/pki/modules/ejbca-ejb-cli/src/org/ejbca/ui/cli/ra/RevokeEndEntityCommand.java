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

import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.ejbca.core.ejb.ra.EndEntityAccessSessionRemote;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionRemote;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.ui.cli.CliUsernameException;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Revokes an end entity in the database, and also revokes all that end entity's certificates.
 *
 * @version $Id: RevokeEndEntityCommand.java 17857 2013-10-17 15:01:49Z mikekushner $
 */
public class RevokeEndEntityCommand extends BaseRaCommand {

    private static final String COMMAND = "revokeendentity";
    private static final String OLD_COMMAND = "revokeuser";
    
    @Override
	public String getSubCommand() { return COMMAND; }
    @Override
	public String getDescription() { return "Revokes an end enity and all certificates for that end entity."; }
    
    @Override
    public String[] getSubCommandAliases() {
        return new String[]{OLD_COMMAND};
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
                getLogger().info("Usage: " + getCommand() + " <username> <reason>");
                getLogger().info(" Reason: unused(0), keyCompromise(1), cACompromise(2), affiliationChanged(3)," +
                		" superseded(4), cessationOfOperation(5), certficateHold(6), removeFromCRL(8),privilegeWithdrawn(9),aACompromise(10)");
                getLogger().info(" Normal reason is 0");
                return;
            }
            String username = args[1];
            int reason = Integer.parseInt(args[2]);
            if ((reason == 7) || (reason < 0) || (reason > 10)) {
            	getLogger().error("Reason must be an integer between 0 and 10 except 7.");
            } else {
            	EndEntityInformation data = ejb.getRemoteSession(EndEntityAccessSessionRemote.class).findUser(getAuthenticationToken(cliUserName, cliPassword), username);
                if (data==null) {
                	getLogger().error("User not found.");
                	return;
                }
                getLogger().info("Found user:");
                getLogger().info("username=" + data.getUsername());
                getLogger().info("dn=\"" + data.getDN() + "\"");
                getLogger().info("Old status=" + data.getStatus());
                // Revoke users certificates
                try {
                    ejb.getRemoteSession(EndEntityManagementSessionRemote.class).revokeUser(getAuthenticationToken(cliUserName, cliPassword), username, reason);
                    data = ejb.getRemoteSession(EndEntityAccessSessionRemote.class).findUser(getAuthenticationToken(cliUserName, cliPassword), username);
                    getLogger().info("New status=" + data.getStatus());
                } catch (AuthorizationDeniedException e) {
                	getLogger().error("Not authorized to revoke user.");
                } catch (ApprovalException e) {
                	getLogger().error("Revocation already requested.");
                } catch (WaitingForApprovalException e) {
                	getLogger().info("Revocation request has been sent for approval.");
                }
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
    }
}
