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

import javax.ejb.Remote;

/**
 * Bridge for local EJB calls that we only want to expose to in test deployments.
 * 
 * @version $Id: CryptoTokenManagementProxySessionRemote.java 16705 2013-04-30 11:55:26Z mikekushner $
 */
@Remote
public interface CryptoTokenManagementProxySessionRemote {

    /** @see CryptoTokenManagementSessionLocal#getCryptoToken(int) */
    CryptoToken getCryptoToken(int cryptoTokenId);
    
    boolean isCryptoTokenNameUsed(final String cryptoTokenName);
}
