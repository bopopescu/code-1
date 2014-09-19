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
package org.ejbca.core.ejb.authentication.web;

import javax.ejb.Remote;

import org.cesecore.authentication.tokens.AuthenticationProvider;

/**
 * @version $Id: WebAuthenticationProviderProxySessionRemote.java 13725 2012-01-10 14:17:21Z mikekushner $
 *
 */
@Remote
public interface WebAuthenticationProviderProxySessionRemote  extends AuthenticationProvider {

}
