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
package org.ejbca.core.ejb.authentication.cli;

import javax.ejb.Local;

import org.cesecore.authentication.tokens.AuthenticationProvider;

/**
 * This interface provides authentication for CLI users. 
 * 
 * @version $Id: CliAuthenticationProviderSessionLocal.java 14270 2012-03-08 08:26:47Z anatom $
 *
 */
@Local
public interface CliAuthenticationProviderSessionLocal extends AuthenticationProvider {

}
