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
package org.cesecore.certificates.certificate;

import java.math.BigInteger;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.ejb.CreateException;
import javax.ejb.Local;

import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.certificate.request.RequestMessage;

/**
 * Local interface for CertificateStoreSession.
 * 
 * @version $Id: CertificateStoreSessionLocal.java 17625 2013-09-20 07:12:06Z netmackan $
 */
@Local
public interface CertificateStoreSessionLocal extends CertificateStoreSession {

    //FIXME: Documentation
    CertificateInfo findFirstCertificateInfo(String issuerDN, BigInteger serno);
    
    /**
     * Stores a certificate without checking authorization. This should be used from other methods where authorization to
     * the CA issuing the certificate has already been checked. For efficiency this method can then be used.
     * 
     * @param incert The certificate to be stored.
     * @param cafp Fingerprint (hex) of the CAs certificate.
     * @param username username of end entity owning the certificate.
     * @param status the status from the CertificateConstants.CERT_ constants
     * @param type Type of certificate (CERTTYPE_ENDENTITY etc from CertificateConstants).
     * @param certificateProfileId the certificate profile id this cert was issued under
     * @param tag a custom string tagging this certificate for some purpose
     * @return true if storage was successful.
     * @throws CreateException if the certificate can not be stored in the database
     */
    boolean storeCertificateNoAuth(AuthenticationToken admin, Certificate incert, String username,
            String cafp, int status, int type, int certificateProfileId, String tag, long updateTime) throws CreateException, AuthorizationDeniedException;

    /**
     * Method to set the status of certificate to revoked or active, without checking for authorization. 
     * This is why it is important that this method is _local only_. 
     * 
     * @param admin Administrator performing the operation
     * @param certificate the certificate to revoke or activate.
     * @param revokeDate when it was revoked
     * @param reason the reason of the revocation. (One of the RevokedCertInfo.REVOCATION_REASON constants.)
     * @param userDataDN if an DN object is not found in the certificate use object from user data instead.
     * @return true if status was changed in the database, false if not, for example if the certificate was already revoked or a null value was passed as certificate
     * @throws CertificaterevokeException (rollback) if certificate does not exist
     */
    boolean setRevokeStatusNoAuth(AuthenticationToken admin, Certificate certificate, Date revokedDate, int reason, String userDataDN)
    	throws CertificateRevokeException;
    
    /**
     * 
     * Changes the revocation date for the certificate identified by the fingerprint. This should only occur in an exceptional circumstance (a revoked 
     * certificate missing a revocation date) and should not be called during standard operations.
     * 
     * This method should only be used in the exceptional circumstance where a revoked certificate lacks a revocation date.  
     * 
     * @param authenticationToken the authenticating end entity
     * @param certificateFingerprint a fingerprint identifying a certificate
     * @param revocationDate the revocation date
     * @throws AuthorizationDeniedException 
     */
    void setRevocationDate(AuthenticationToken authenticationToken, String certificateFingerprint, Date revocationDate) throws AuthorizationDeniedException;
    
    /**
     * Fetch a List of all certificate fingerprints and corresponding username
     * 
     * @param cas A list of CAs that the sought certificates should be issued from
     * @param certificateProfiles A list if certificateprofiles to sort from. Will be ignored if left empty. 
     * @param activeNotifiedExpireDateMin The minimal date for expiration notification
     * @param activeNotifiedExpireDateMax The maxmimal date for expiration notification
     * @param activeExpireDateMin the current rune timestamp + the threshold 
     * 
     * @return [0] = (String) fingerprint, [1] = (String) username
     */
    List<Object[]> findExpirationInfo(Collection<String> cas, Collection<Integer> certificateProfiles, long activeNotifiedExpireDateMin, long activeNotifiedExpireDateMax, long activeExpireDateMin);
    
    /**
     * Query if we have a clear cut case where no username or the provided username is the only user of the subjectDN and subjectKeyId.
     * @param issuerDN The CA's subject
     * @param subjectDN The entity's subject
     * @param subjectKeyId The entity's subjectKeyId
     * @param username the entity's username
     * @return true if there is no other end entity under this issuer with the same subjectKeyId or subjectDN
     */
    boolean isOnlyUsernameForSubjectKeyIdOrDnAndIssuerDN(String issuerDN, byte[] subjectKeyId, String subjectDN, String username);

    /**
     * Find the most recently issued/updated certificate for a public key
     * 
     * @param subjectKeyId Is the ASN.1 SubjectKeyIdentifier of the public key as a byte array
     * @return null or the certificate which is active, matches the argument and has the latest updateTime
     * @see org.cesecore.keys.util.KeyTools#createSubjectKeyId(java.security.PublicKey)
     */
    Certificate findMostRecentlyUpdatedActiveCertificate(byte[] subjectKeyId);

    /** @return the username or null if no row was found for this certificate fingerprint */
    String findUsernameByFingerprint(String fingerprint);

    /** 
     * Tries to get an issuerDN/serialNumber pair from the request, and see if we have that CA certificate in the certificate store. If we have
     * the CA dn, in CESeCore normalized form, is returned. 
     * @param req the request message that might contain an issued DN
     * @return issuer DN or null if it does not exist in the 
     */
    String getCADnFromRequest(RequestMessage req);
    
    /**
     * Will test if there is a unique index/constraint for (certificate serial number,issuer DN) first time it is run.
     * @return returns true if there is a database index for unique certificate serial number / issuer DN.
     */
    boolean isUniqueCertificateSerialNumberIndex();

    /** ONLY use in Unit tests.
     * Required by multiple entry unit tests since isUniqueCertificateSerialNumberIndex is a static variable. */
    void resetUniqueCertificateSerialNumberIndex();

    /** ONLY use in Unit tests.
     * Sets the check for present certificate serial number unique index to specified value. Can be used to override safety check that the 
     * index exists. */
    void setUniqueCertificateSerialNumberIndex(final Boolean value);

    /** Checks for present certificate serial number unique index in a new transaction in order to avoid rollback, since we can expect SQL exceptions here. 
     * Should not be used externally. */
    void checkForUniqueCertificateSerialNumberIndexInTransaction();

}
