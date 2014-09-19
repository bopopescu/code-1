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

import javax.ejb.Local;

import org.cesecore.authentication.tokens.AuthenticationProvider;

/**
 * Provides authentication for web service users.
 * 
 * @version $Id: WebAuthenticationProviderSessionLocal.java 12547 2011-09-12 13:28:22Z mikekushner $
 *
 */
@Local
public interface WebAuthenticationProviderSessionLocal extends AuthenticationProvider {

}