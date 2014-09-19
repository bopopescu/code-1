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
package org.ejbca.ui.cli.cryptotoken;

import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.keys.token.CryptoTokenInfo;
import org.cesecore.keys.token.CryptoTokenManagementSessionRemote;

/**
 * CryptoToken EJB CLI command. See {@link #getDescription()} implementation.
 * 
 * @version $Id: CryptoTokenActivateCommand.java 16676 2013-04-29 10:53:13Z jeklund $
 */
public class CryptoTokenActivateCommand extends BaseCryptoTokenCommand {

    @Override
    public String getSubCommand() {
        return "activate";
    }

    @Override
    public String getDescription() {
        return "Activate CryptoToken";
    }

    @Override
    public void executeCommand(Integer cryptoTokenId, String[] args) {
        if (args.length < 3) {
            getLogger().info("Description: " + getDescription());
            getLogger().info("Usage: " + getCommand() + " <name of CryptoToken> <pin or \"null\" to prompt>");
            return;
        }
        final char[] authenticationCode = getAuthenticationCode(args[2]);
        try {
            final CryptoTokenManagementSessionRemote cryptoTokenManagementSession = ejb.getRemoteSession(CryptoTokenManagementSessionRemote.class);
            final CryptoTokenInfo cryptoTokenInfo = cryptoTokenManagementSession.getCryptoTokenInfo(getAdmin(), cryptoTokenId.intValue());
            final boolean usingAutoActivation = cryptoTokenInfo.isAutoActivation();
            cryptoTokenManagementSession.activate(getAdmin(), cryptoTokenId, authenticationCode);
            if (cryptoTokenManagementSession.isCryptoTokenStatusActive(getAdmin(), cryptoTokenId)) {
                if (usingAutoActivation) {
                    getLogger().info("CryptoToken activated successfully using auto-activation PIN. (The supplied PIN was ignored.)");
                } else {
                    getLogger().info("CryptoToken activated successfully using supplied PIN.");
                }
            } else {
                if (usingAutoActivation) {
                    getLogger().error("Failed to activate CryptoToken using auto-activation PIN even though request was process successfully. (The supplied PIN was ignored.)");
                } else {
                    getLogger().warn("CryptoToken still not active even though request was processed successfully.");
                }
            }
        } catch (AuthorizationDeniedException e) {
            getLogger().info(e.getMessage());
        } catch (Exception e) {
            getLogger().info("CryptoToken activation failed: " + e.getMessage());
        }
    }
}
