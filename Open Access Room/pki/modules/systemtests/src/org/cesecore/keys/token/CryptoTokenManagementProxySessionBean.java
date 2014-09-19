/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.keys.token;

import javax.ejb.EJB;
import javax.ejb.Stateless;

import org.cesecore.jndi.JndiConstants;

/**
 * @see CryptoTokenManagementProxySessionRemote
 * @version $Id: CryptoTokenManagementProxySessionBean.java 16705 2013-04-30 11:55:26Z mikekushner $
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "CryptoTokenManagementProxySessionRemote")
public class CryptoTokenManagementProxySessionBean implements CryptoTokenManagementProxySessionRemote {

    @EJB
    private CryptoTokenSessionLocal cryptoTokenSession;
    @EJB
    private CryptoTokenManagementSessionLocal cryptoTokenManagementSession;

    @Override
    public CryptoToken getCryptoToken(int cryptoTokenId) {
        return cryptoTokenManagementSession.getCryptoToken(cryptoTokenId);
    }

    @Override
    public boolean isCryptoTokenNameUsed(String cryptoTokenName) {
        return cryptoTokenSession.isCryptoTokenNameUsed(cryptoTokenName);
    }
}
