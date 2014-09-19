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
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.ejbca.ui.cli.CliUsernameException;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.util.CliTools;

/**
 * Export root CA certificate.
 *
 * @version $Id: CaGetRootCertCommand.java 17764 2013-10-09 13:01:40Z mikekushner $
 */
public class CaGetRootCertCommand extends BaseCaAdminCommand {

    @Override
    public String getSubCommand() { return "getrootcert"; }
    @Override
    public String getDescription() { return "Save root CA certificate (PEM- or DER-format) to file"; }

    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            args = parseUsernameAndPasswordFromArgs(args);
        } catch (CliUsernameException e) {
            return;
        }

		// Get and remove switches
		List<String> argsList = CliTools.getAsModifyableList(args);
		boolean pem = !argsList.remove("-der");
		args = argsList.toArray(new String[argsList.size()]);
		// Parse the rest of the arguments
        if (args.length < 3) {
        	getLogger().info("Description: " + getDescription());
            getLogger().info("Usage: " + getCommand() + " <caname> <filename> <-der>");
			getLogger().info(" -der    Use DER encoding. Default is PEM encoding.");
        	return;
        }		
        String caname = args[1];
        String filename = args[2];
        try {
        	CryptoProviderTools.installBCProvider();
            ArrayList<Certificate> chain = new ArrayList<Certificate>(getCertChain(getAuthenticationToken(cliUserName, cliPassword), caname));
            if (chain.size() > 0) {
                Certificate rootcert = (Certificate)chain.get(chain.size()-1);
 
                FileOutputStream fos = new FileOutputStream(filename);
                if (pem) {		
                    fos.write(CertTools.getPemFromCertificateChain(chain));
                } else {					
                    fos.write(rootcert.getEncoded());
                }				
                fos.close();
				getLogger().info("Wrote Root CA certificate to '" + filename + "' using " + (pem?"PEM":"DER") + " encoding.");
            } else {
            	getLogger().error("No CA certificate found.");
            }
        } catch (Exception e) {			
            throw new ErrorAdminCommandException(e);
        }        
    }
}
