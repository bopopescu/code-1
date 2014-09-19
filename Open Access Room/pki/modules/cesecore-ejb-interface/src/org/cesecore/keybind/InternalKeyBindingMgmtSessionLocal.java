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
package org.cesecore.keybind;

import java.util.List;

import javax.ejb.Local;

import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;

/**
 * @see InternalKeyBindingMgmtSession
 * @version $Id: InternalKeyBindingMgmtSessionLocal.java 17970 2013-10-24 14:40:11Z samuellb $
 */
@Local
public interface InternalKeyBindingMgmtSessionLocal extends InternalKeyBindingMgmtSession {
    
    /**
     * Returns a list of all internal key bindings of a certain type, as {@link InternalKeyBindingInfo}s
     * 
     * @param internalKeyBindingType the key binding type
     * @return a list of all internal key bindings of that type, as {@link InternalKeyBindingInfo}s
     */
    List<InternalKeyBindingInfo> getAllInternalKeyBindingInfos(String internalKeyBindingType);
    
    /**
     * Internal (local only) method to get keybinding info without logging the authorization check
     * (the auth check is performed though).
     * 
     * @see getInternalKeyBindingInfo
     */
    InternalKeyBindingInfo getInternalKeyBindingInfoNoLog(AuthenticationToken authenticationToken, int internalKeyBindingId) throws AuthorizationDeniedException;
}
