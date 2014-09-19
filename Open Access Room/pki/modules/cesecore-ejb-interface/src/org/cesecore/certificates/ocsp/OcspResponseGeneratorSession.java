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
package org.cesecore.certificates.ocsp;

import java.io.IOException;
import java.security.cert.X509Certificate;

import org.bouncycastle.cert.ocsp.OCSPException;
import org.cesecore.certificates.ocsp.exception.MalformedRequestException;
import org.cesecore.certificates.ocsp.logging.AuditLogger;
import org.cesecore.certificates.ocsp.logging.TransactionLogger;

/**
 * This interface is used to generate OCSP responses.
 * 
 * See {@link https://www.cesecore.eu/mediawiki/index.php/Functional_Specifications_(ADV_FSP)#OCSP_Response_Generation}
 * 
 * @version $Id: OcspResponseGeneratorSession.java 17988 2013-10-25 14:16:06Z mikekushner $
 * 
 */
public interface OcspResponseGeneratorSession {

    /**
     * This method delivers an OCSP response to a given request, as provided in the byte[] parameter.
     * 
     * @param authenticationToken An authentication token for the user performing the operation.
     * @param requestBytes a byte array representing an encoded OCSPRequest.
     * @param requestCertificates An array of Certificates from the original HttpServletRequest
     * @param remoteAddress Remote address, most likely extracted from the HttpServletRequest
     * @param remoteHost Remote host, most likely extracted from the HttpServletRequest
     * @param auditLogger The AuditLogger to use for this transaction
     * @param transactionLogger The TransactionLogger to use for this transaction
     * 
     * @return a signed and encoded OCSPResponse wrapped in an OcspResponseInformation object
     * @throws MalformedRequestException if the request byte array was invalid.
     * @throws IOException 
     * @throws OCSPException if OCSP response generation fails
     */
    OcspResponseInformation getOcspResponse(byte[] requestBytes,
            X509Certificate[] requestCertificates, String remoteAddress, String remoteHost, StringBuffer requestUrl, AuditLogger auditLogger,
            TransactionLogger transactionLogger) throws MalformedRequestException, IOException, OCSPException;
    
    /**
     * Reloads the chache of OCSP signers.
     */
    void reloadOcspSigningCache();

}
