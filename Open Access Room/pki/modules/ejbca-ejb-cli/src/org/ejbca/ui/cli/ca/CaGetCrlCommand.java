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

import java.io.FileOutputStream;
import java.util.List;

import org.cesecore.certificates.crl.CrlStoreSessionRemote;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.ejbca.ui.cli.CliUsernameException;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.util.CliTools;

/**
 * Retrieves the latest CRL from a CA.
 *
 * @version $Id: CaGetCrlCommand.java 17764 2013-10-09 13:01:40Z mikekushner $
 */
public class CaGetCrlCommand extends BaseCaAdminCommand {

    @Override
    public String getSubCommand() { return "getcrl"; }
    @Override
    public String getDescription() { return "Retrieves the latest CRL from a CA"; }

    @Override
    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            args = parseUsernameAndPasswordFromArgs(args);
        } catch (CliUsernameException e) {
            return;
        }
        
        // Get and remove switches
		List<String> argsList = CliTools.getAsModifyableList(args);
		boolean deltaSelector = argsList.remove("-delta");
		boolean pem = argsList.remove("-pem");
		args = argsList.toArray(new String[argsList.size()]);
		// Parse the rest of the arguments
		if (args.length < 3) {
			getLogger().info("Description: " + getDescription());
			getLogger().info("Usage: " + getCommand() + " [-delta] [-pem] <caname> <outfile>");
			getLogger().info(" -delta  Fetch the latest delta CRL. Default is regular CRL.");
			getLogger().info(" -pem    Use PEM encoding. Default is DER encoding.");
			return;
		}
		try {
			CryptoProviderTools.installBCProvider();
			// Perform CRL fetch
			String caname = args[1];
			String outfile = args[2];
			String issuerdn = getIssuerDN(getAuthenticationToken(cliUserName, cliPassword), caname);
			byte[] crl = ejb.getRemoteSession(CrlStoreSessionRemote.class).getLastCRL(issuerdn, deltaSelector);
			if (crl != null) {
				FileOutputStream fos = new FileOutputStream(outfile);
				if (pem) {		
					fos.write(CertTools.getPEMFromCrl(crl));
				} else {					
					fos.write(crl);
				}
				fos.close();				
				getLogger().info("Wrote latest " + (deltaSelector?"delta ":"") + "CRL to " + outfile + " using " + (pem?"PEM":"DER") + " format");
			} else {
				getLogger().info("No " + (deltaSelector?"delta ":"") + "CRL exists for CA "+caname+".");				
			}
		} catch (Exception e) {
			throw new ErrorAdminCommandException(e);
		}
    }
}
