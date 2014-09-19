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
package org.ejbca.core.ejb.audit;

import javax.ejb.Remote;

/**
 * @see EjbcaAuditorSession
 * @version $Id: EjbcaAuditorTestSessionRemote.java 13028 2011-10-28 07:34:34Z anatom $
 */
@Remote
public interface EjbcaAuditorTestSessionRemote extends EjbcaAuditorSession {
}
