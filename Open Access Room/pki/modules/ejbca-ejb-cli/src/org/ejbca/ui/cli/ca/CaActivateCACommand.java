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

import javax.security.auth.login.FailedLoginException;

import org.cesecore.certificates.ca.CAConstants;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.keys.token.CryptoTokenAuthenticationFailedException;
import org.cesecore.keys.token.CryptoTokenManagementSessionRemote;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.ui.cli.CliUsernameException;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Activates the specified HSM CA.
 * 
 * @version $Id: CaActivateCACommand.java 9345 2010-07-01 15:51:20Z mikekushner$
 */
public class CaActivateCACommand extends BaseCaAdminCommand {

    @Override
    public String getSubCommand() {
        return "activateca";
    }

    @Override
    public String getDescription() {
        return "Activates the specified HSM CA";
    }

    @Override
    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            args = parseUsernameAndPasswordFromArgs(args);
        } catch (CliUsernameException e) {
            return;
        }
        try {
            if (args.length < 2) {
                getLogger().info("Description: " + getDescription());
                getLogger().info("Usage: " + getCommand() + " <CA name> [<authorization code>]");
                getLogger().info(" Leaving out authorization code will prompt for it.");
                return;
            }
            String caname = args[1];
            String authorizationcode = null;
            if (args.length > 2) {
                authorizationcode = args[2];
            } else {
                getLogger().info("Enter authorization code: ");
                // Read the password, but mask it so we don't display it on the
                // console
                authorizationcode = String.valueOf(System.console().readPassword());
            }
            CryptoProviderTools.installBCProvider();
            // Get the CAs info and id
            CAInfo cainfo = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getCAInfo(getAuthenticationToken(cliUserName, cliPassword), caname);
            if (cainfo == null) {
                getLogger().error("Error: CA " + caname + " cannot be found");
                return;
            }
            // Check that CA has correct status.
            final int cryptoTokenId = cainfo.getCAToken().getCryptoTokenId();
            final CryptoTokenManagementSessionRemote cryptoTokenManagementSession = ejb.getRemoteSession(CryptoTokenManagementSessionRemote.class);
            final boolean tokenOffline = !cryptoTokenManagementSession.isCryptoTokenStatusActive(getAuthenticationToken(cliUserName, cliPassword), cryptoTokenId);
            if (cainfo.getStatus()==CAConstants.CA_OFFLINE || tokenOffline) {
                try {
                    if (cainfo.getStatus() == CAConstants.CA_OFFLINE) {
                        ejb.getRemoteSession(CAAdminSessionRemote.class).activateCAService(getAuthenticationToken(cliUserName, cliPassword), cainfo.getCAId());
                        getLogger().info("CA Service activated.");
                    }
                    if (tokenOffline) {
                        cryptoTokenManagementSession.activate(getAuthenticationToken(cliUserName, cliPassword), cryptoTokenId, authorizationcode.toCharArray());
                        getLogger().info("CA's CryptoToken activated.");
                    }
                } catch (CryptoTokenAuthenticationFailedException e) {
                    getLogger().error("CA Token authentication failed.");
                    getLogger().error(e.getMessage());
                    Throwable t = e.getCause();
                    while (t != null) {
                        if (t instanceof FailedLoginException) {
                            // If it's an HSM the next exception will be a
                            // PKCS11 exception. We don't want to search
                            // directly for that though, because then we
                            // will import sun specific classes, and we don't
                            // want that.
                            t = t.getCause();
                            if (t != null) {
                                getLogger().error(t.getMessage());
                                break;
                            }
                        } else {
                            t = t.getCause();
                        }
                    }
                    getLogger().debug("Exception: ", e);
                } catch (ApprovalException e) {
                    getLogger().error("CA Token activation approval request already exists.");
                } catch (WaitingForApprovalException e) {
                    getLogger().error("CA requires an approval to be activated. A request have been sent to authorized admins.");
                }
            } else {
                getLogger().error("CA or CAToken must be offline to be activated.");
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
    }
}
