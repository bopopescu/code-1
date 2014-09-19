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

import java.security.cert.Certificate;
import java.util.Collection;

import org.cesecore.certificates.certificate.CertificateStoreSessionRemote;
import org.cesecore.util.CertTools;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Output all certificates for an end entity.
 *
 * @version $Id: GetEndEntityCertificateCommand.java 17764 2013-10-09 13:01:40Z mikekushner $
 */
public class GetEndEntityCertificateCommand extends BaseRaCommand {
    
    private static final String COMMAND = "getendentitycert";
    private static final String OLD_COMMAND = "getusercert";
    
    @Override
	public String getSubCommand() { return COMMAND; }
    @Override
    public String getDescription() { return "Output all certificates for an end entity"; }
    
    @Override
    public String[] getSubCommandAliases() {
        return new String[]{OLD_COMMAND};
    }

    @Override
    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            if (args.length < 2) {
    			getLogger().info("Description: " + getDescription());
                getLogger().info("Usage: " + getCommand() + " <username>");
                return;
            }
            final String username = args[1];
            final Collection<Certificate> data = ejb.getRemoteSession(CertificateStoreSessionRemote.class).findCertificatesByUsername(username);
            if (data != null) {
            	getLogger().info(new String(CertTools.getPemFromCertificateChain(data)));
            } else {
            	getLogger().info("End Entity with username '" + username + "' does not exist.");
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
    }
}
