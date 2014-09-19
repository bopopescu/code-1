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
import org.cesecore.keys.token.CryptoTokenManagementSessionRemote;
import org.cesecore.keys.token.CryptoTokenOfflineException;

/**
 * CryptoToken EJB CLI command. See {@link #getDescription()} implementation.
 * 
 * @version $Id: CryptoTokenRemoveCommand.java 16177 2013-01-22 13:57:07Z anatom $
 */
public class CryptoTokenRemoveCommand extends BaseCryptoTokenCommand {

    @Override
    public String getSubCommand() {
        return "removekey";
    }

    @Override
    public String getDescription() {
        return "Remove a key pair";
    }

    @Override
    public void executeCommand(Integer cryptoTokenId, String[] args) {
        if (args.length < 3) {
            getLogger().info("Description: " + getDescription());
            getLogger().info("Usage: " + getCommand() + " <name of CryptoToken> <key pair alias>");
            return;
        }
        final String keyPairAlias = args[2];
        try {
            ejb.getRemoteSession(CryptoTokenManagementSessionRemote.class).removeKeyPair(getAdmin(), cryptoTokenId, keyPairAlias);
            getLogger().info("Key pair removed successfully.");
        } catch (AuthorizationDeniedException e) {
            getLogger().info(e.getMessage());
        } catch (CryptoTokenOfflineException e) {
            getLogger().info("CryptoToken is not active. You need to activate the CryptoToken before you can interact with its content.");
        } catch (Exception e) {
            getLogger().info("Key pair removal failed: " + e.getMessage());
        }
    }
}
