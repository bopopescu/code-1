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

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.InvalidAlgorithmException;
import org.cesecore.keybind.InternalKeyBindingFactory;
import org.cesecore.keybind.InternalKeyBindingMgmtSessionRemote;
import org.cesecore.keybind.InternalKeyBindingNameInUseException;
import org.cesecore.keybind.InternalKeyBindingStatus;
import org.cesecore.keys.token.CryptoTokenManagementSessionRemote;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.ejbca.util.CliTools;

/**
 * See getDescription().
 * 
 * @version $Id: InternalKeyBindingCreateCommand.java 18264 2013-12-10 18:01:49Z mikekushner $
 */
public class InternalKeyBindingCreateCommand extends BaseInternalKeyBindingCommand {

    @Override
    public String getSubCommand() {
        return "create";
    }

    @Override
    public String getDescription() {
        return "Creates a new InternalKeyBinding.";
    }

    @Override
    protected boolean failIfInternalKeyBindIsMissing() {
        return false;
    }

    @Override
    public void executeCommand(Integer internalKeyBindingId, String[] args) throws AuthorizationDeniedException, CryptoTokenOfflineException,
            InternalKeyBindingNameInUseException, InvalidAlgorithmException {
        final InternalKeyBindingMgmtSessionRemote internalKeyBindingMgmtSession = ejb.getRemoteSession(InternalKeyBindingMgmtSessionRemote.class);
        if (args.length < 8) {
            getLogger().info("Description: " + getDescription());
            getLogger()
                    .info("Usage: "
                            + getCommand()
                            + " <name> <type> <status> <certificate fingerprint> <crypto token name> <key pair alias> <signature algorithm> [--property key1=value1 --property key2=value2 ...]");
            getLogger().info("");
            showTypesProperties();
            showStatuses();
            showSigAlgs();
            return;
        }
        // Start by extracting any property
        final Map<String, String> dataMap = new LinkedHashMap<String, String>();
        final List<String> argsList = CliTools.getAsModifyableList(args);
        while (true) {
            final String propertyArg = CliTools.getAndRemoveParameter("--property", argsList);
            if (propertyArg == null) {
                break;
            }
            int indexOfEqualsSign = propertyArg.indexOf('=');
            if (indexOfEqualsSign == -1) {
                getLogger().info(" Ignoring --property with value " + propertyArg + ". The correct format is \"key=value\"");
                continue;
            }
            String key = propertyArg.substring(0, indexOfEqualsSign);
            String value = propertyArg.substring(indexOfEqualsSign + 1);
            dataMap.put(key, value);
        }
        args = CliTools.getAsArgs(argsList);
        // Parse static arguments
        final String name = args[1];
        final String type = args[2];
        if (!InternalKeyBindingFactory.INSTANCE.existsTypeAlias(type)) {
            getLogger().error("KeyBinding of type " + type + " does not exist.");
            return;
        }
        //Validate all properties
        Map<String, Serializable> validatedProperties = validateProperties(type, dataMap);
        if(validatedProperties == null) {
            return;
        }
        final InternalKeyBindingStatus status = InternalKeyBindingStatus.valueOf(args[3].toUpperCase());
        final String certificateId = "null".equalsIgnoreCase(args[4]) ? null : args[4];
        final int cryptoTokenId = ejb.getRemoteSession(CryptoTokenManagementSessionRemote.class).getIdFromName(args[5]);
        final String keyPairAlias = args[6];
        final String signatureAlgorithm = args[7];
        int internalKeyBindingIdNew = internalKeyBindingMgmtSession.createInternalKeyBinding(getAdmin(), type, name, status, certificateId,
                cryptoTokenId, keyPairAlias, signatureAlgorithm, validatedProperties);
        getLogger().info("InternalKeyBinding with id " + internalKeyBindingIdNew + " created successfully.");
    }

}
