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
 
package org.ejbca.core.model.ca.publisher;

import java.security.cert.Certificate;
import java.util.Properties;

import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.certificates.endentity.ExtendedInformation;





/**
 * Interface containing methods that need to be implemented in order 
 * to have a custom publisher. All Custom publishers must implement this interface.
 * 
 * @version $Id: ICustomPublisher.java 12931 2011-10-19 12:37:56Z anatom $
 */

public interface ICustomPublisher {
	
	/**
	 *  Method called to all newly created ICustomPublishers to set it up with
	 *  saved configuration.
	 */
	public abstract void init(Properties properties);

	/**
	 * @see org.ejbca.core.model.ca.publisher.BasePublisher#storeCertificate
	 */    
	public abstract boolean storeCertificate(AuthenticationToken admin, Certificate incert, String username, String password, String userDN, String cafp, int status, int type, long revocationDate, int revocationReason, String tag, int certificateProfileId, long lastUpdate, ExtendedInformation extendedinformation)throws PublisherException;
	
	/**
	 * @see org.ejbca.core.model.ca.publisher.BasePublisher#storeCRL
	 */ 
	public abstract boolean storeCRL(AuthenticationToken admin, byte[] incrl, String cafp, int number, String userDN)throws PublisherException;
	
	/**
	 * @see org.ejbca.core.model.ca.publisher.BasePublisher#testConnection
	 */    
	public abstract void testConnection() throws PublisherConnectionException;
	

}

