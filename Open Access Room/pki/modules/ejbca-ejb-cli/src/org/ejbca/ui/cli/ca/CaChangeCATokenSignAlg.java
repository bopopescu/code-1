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

import java.util.Collection;

import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.ca.catoken.CAToken;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.ui.cli.CliUsernameException;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Changes the signature algorithm and possible keyspec of a CA token.
 *
 * @version $Id: CaChangeCATokenSignAlg.java 17764 2013-10-09 13:01:40Z mikekushner $
 */
public class CaChangeCATokenSignAlg extends BaseCaAdminCommand {

   
    @Override
	public String getSubCommand() { return "changecatokensignalg"; }
    @Override
	public String getDescription() { return "Changes the signature algorithm and possible keyspec of a CA token"; }

	@Override
    public void execute(String[] args) throws ErrorAdminCommandException {
		getLogger().trace(">execute()");
		CryptoProviderTools.installBCProvider(); // need this for CVC certificate
        try {
            args = parseUsernameAndPasswordFromArgs(args);
        } catch (CliUsernameException e) {
            return;
        }
		if ( args.length<3 ) {
			usage(cliUserName, cliPassword);
			return;
		}
		try {
			String caName = args[1];
			CAInfo cainfo = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getCAInfo(getAuthenticationToken(cliUserName, cliPassword), caName);
			String signAlg = args[2];
			getLogger().info("Setting new signature algorithm: " + signAlg);
            final CAToken caToken = cainfo.getCAToken();
            caToken.setSignatureAlgorithm(signAlg);
            cainfo.setCAToken(caToken);
			ejb.getRemoteSession(CAAdminSessionRemote.class).editCA(getAuthenticationToken(cliUserName, cliPassword), cainfo);
			getLogger().info("CA token signature algorithm for CA changed.");
		} catch (Exception e) {
			getLogger().error(e.getMessage());
			usage(cliUserName, cliPassword);
		}
		getLogger().trace("<execute()");
	}
    
	private void usage(String cliUserName, String cliPassword) {
		getLogger().info("Description: " + getDescription());
		getLogger().info("Usage: " + getCommand() + " <caname> <signature alg>");
		getLogger().info(" Signature alg is one of SHA1WithRSA, SHA256WithRSA, SHA256WithRSAAndMGF1, SHA224WithECDSA, SHA256WithECDSA, or any other string available in the admin-GUI.");
		getLogger().info(" Existing CAs: ");
		try {
			// Print available CAs
			Collection<Integer> cas = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getAuthorizedCAs(getAuthenticationToken(cliUserName, cliPassword));
			for (Integer caid : cas) {
				CAInfo info = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getCAInfo(getAuthenticationToken(cliUserName, cliPassword), caid);
				getLogger().info("    "+info.getName()+": "+info.getCAToken().getSignatureAlgorithm());				
			}
		} catch (Exception e) {
			e.printStackTrace();
			getLogger().error("<unable to fetch available CA>");
		}
	}
}
