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
package org.ejbca.core.ejb.ca.caadmin;

import java.security.InvalidKeyException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.ejb.EJBException;

import org.bouncycastle.operator.OperatorCreationException;
import org.cesecore.CesecoreException;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CAOfflineException;
import org.cesecore.certificates.ca.InvalidAlgorithmException;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceInfo;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceNotActiveException;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceRequest;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceRequestException;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceResponse;
import org.cesecore.certificates.ca.extendedservices.IllegalExtendedCAServiceRequestException;
import org.cesecore.certificates.certificate.request.RequestMessage;
import org.cesecore.certificates.certificate.request.ResponseMessage;
import org.cesecore.keys.token.CryptoToken;
import org.cesecore.keys.token.CryptoTokenAuthenticationFailedException;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.cesecore.keys.token.IllegalCryptoTokenException;
import org.cesecore.keys.token.p11.exception.NoSuchSlotException;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;

/**
 * Administrates and manages CAs in EJBCA system.
 * 
 * @version $Id: CAAdminSession.java 17556 2013-09-06 13:05:20Z mikekushner $
 */
public interface CAAdminSession {

    /**
     * Creates a certificate request that should be sent to External CA for
     * processing. This command will not affect the current issuing key or
     * certificate.
     * 
     * @param authenticationToken
     *            the administrator performing the action
     * @param caid
     *            id of the CA that should create the request
     * @param cachain
     *            A Collection of CA-certificates, can be either a collection
     *            of Certificate or byte[], or even empty collection or null.
     * @param nextSignKeyAlias
     *            The next key alias to use for this request.
     *            If null, then a new key pair will be generated named using
     *            the key sequence.
     * @return request message in binary format, can be a PKCS10 or CVC request
     */
    public byte[] makeRequest(AuthenticationToken authenticationToken, int caid, Collection<?> certChain, String nextSignKeyAlias) throws AuthorizationDeniedException, CertPathValidatorException, CryptoTokenOfflineException;

    /**
     * If the CA can do so, this method signs another entity's CSR, for
     * authentication. Prime example of for EU EAC ePassports where the DVs
     * initial certificate request is signed by the CVCA. The signature
     * algorithm used to sign the request will be whatever algorithm the CA uses
     * to sign certificates.
     * 
     * @param caid the CA that should sign the request
     * @param request
     *            binary certificate request, the format should be understood by
     *            the CA
     * @return binary certificate request, which is the same as passed in except
     *         also signed by the CA, or it might be the exact same if the CA
     *         does not support request signing
     */
    public byte[] createAuthCertSignRequest(AuthenticationToken authenticationToken, int caid, byte[] certSignRequest)
            throws AuthorizationDeniedException, CADoesntExistsException, CryptoTokenOfflineException;

    /**
     * Receives a certificate response from an external CA and sets the newly
     * created CAs status to active.
     * 
     * @param admin
     *            The administrator performing the action
     * @param caid
     *            The caid (DN.hashCode()) of the CA that is receiving this
     *            response
     * @param responsemessage
     *            X509ResponseMessage with the certificate issued to this CA
     * @param chain
     *            an optional collection with the CA certificate(s), or null. If
     *            given the complete chain (except this CAs own certificate must
     *            be given). The contents can be either Certificate objects, or byte[]'s with DER encoded certificates.
     * @param nextKeyAlias compare received certificate to this alias's public key
     * @throws EjbcaException
     */
    public void receiveResponse(AuthenticationToken authenticationToken, int caid, ResponseMessage responsemessage, Collection<?> cachain, String nextKeyAlias)
            throws AuthorizationDeniedException, CertPathValidatorException, EjbcaException, CesecoreException;

    /**
     * Processes a Certificate Request from an external CA.
     * 
     * @param cainfo
     *            the info for the CA that should be created, or already exists.
     *            Don't forget to set signedBy in the info.
     */
    public ResponseMessage processRequest(AuthenticationToken admin, CAInfo cainfo,
            RequestMessage requestmessage) throws CAExistsException, CADoesntExistsException,
            AuthorizationDeniedException, CryptoTokenOfflineException;

    /** Add an external CA's certificate as a CA */
    public void importCACertificate(AuthenticationToken admin, String caname, Collection<Certificate> certificates) throws AuthorizationDeniedException, CAExistsException, IllegalCryptoTokenException;

    /**
     * Inits an external CA service. this means that a new key and certificate
     * will be generated for this service, if it exists before. If it does not
     * exist before it will be created.
     */
    public void initExternalCAService(AuthenticationToken admin, int caid, ExtendedCAServiceInfo info)
            throws CADoesntExistsException, AuthorizationDeniedException, IllegalCryptoTokenException, CAOfflineException;

    /**
     * Unlike the similarily named method initializeAndUpgradeCAs(), this method initializes a
     * previously uninitialized CA by setting its status to active and generating certificate chains
     * for it. 
     * 
     * @param authenticationToken an authentication token
     * @param caInfo representing the CA
     * @throws AuthorizationDeniedException if user was denied authorization to edit CAs 
     * @throws CryptoTokenOfflineException if the keystore defined by the cryptotoken in caInfo has no keys 
     * @throws InvalidKeyException if the cryptotoken owned by this CA lacks keystores
     * @throws CADoesntExistsException if the CA defined by caInfo doesn't exist.
     * @throws InvalidAlgorithmException 
     * @throws CryptoTokenAuthenticationFailedException 
     * @throws CAExistsException 
     * @throws IllegalCryptoTokenException 
     */
    void initializeCa(AuthenticationToken authenticationToken, CAInfo caInfo) throws AuthorizationDeniedException, CryptoTokenOfflineException,
            InvalidKeyException, CADoesntExistsException, CAExistsException, CryptoTokenAuthenticationFailedException, InvalidAlgorithmException, IllegalCryptoTokenException;
    
    /**
     * Renews a existing CA certificate using the requested keys or by
     * generating new keys. The specified notBefore date will be used. 
     * Other data about the new CA is taken from database. This
     * method is used for renewing CAs internally in EJBCA. For renewing CAs
     * signed by external CAs, makeRequest is used to generate a certificate
     * request.
     * 
     * @param caid the caid of the CA that will be renewed
     * @param nextSignKeyAlias
     *            The cryptoTokenAlias to use for the next keys or null to
     *            generate a new key pair using the CA key sequence.
     * @param customNotBefore 
     *            date to use as notBefore date in the new certificate
     *            or null if not custom date should be used which means 
     *            that the current time will be used (normal case).
     * @param createLinkCertificate
     *            generates an additional certificate stored in the CA object
     *            with the next keys signed by the current keys. 
     * @throws AuthorizationDeniedException 
     * @throws CryptoTokenOfflineException 
     * @throws CryptoTokenAuthenticationFailedException 
     */
    public void renewCA(AuthenticationToken administrator, int caid, String nextSignKeyAlias, Date customNotBefore, boolean createLinkCertificate) throws AuthorizationDeniedException, CryptoTokenOfflineException, CryptoTokenAuthenticationFailedException;

    /**
     * Renews a existing CA certificate using the requested keys or by
     * generating new keys. The specified notBefore date will be used. 
     * Other data about the new CA is taken from database. This
     * method is used for renewing CAs internally in EJBCA. For renewing CAs
     * signed by external CAs, makeRequest is used to generate a certificate
     * request.
     * 
     * @param caid the caid of the CA that will be renewed
     * @param regenerateKeys
     *            if true and the CA have a soft CAToken the keys are
     *            regenerated before the certificate request.
     * @param customNotBefore 
     *            date to use as notBefore date in the new certificate
     *            or null if not custom date should be used which means 
     *            that the current time will be used (normal case).
     * @param createLinkCertificate
     *            generates an additional certificate stored in the CA object
     *            with the new keys signed by the current keys.
     *            For CVC CAs this is ignored and the link certificate is always generated.
     * @throws AuthorizationDeniedException 
     * @throws CryptoTokenOfflineException 
     * @throws CryptoTokenAuthenticationFailedException 
     */
    public void renewCA(AuthenticationToken admin, int caid, boolean regenerateKeys, Date customNotBefore, boolean createLinkCertificate) throws CADoesntExistsException,
            AuthorizationDeniedException, java.security.cert.CertPathValidatorException, CryptoTokenOfflineException, CryptoTokenAuthenticationFailedException;

    /**
     * Method that revokes the CA. After this is all certificates created by
     * this CA revoked and a final CRL is created.
     * 
     * @param reason one of RevokedCertInfo.REVOCATION_REASON values.
     */
    public void revokeCA(AuthenticationToken admin, int caid, int reason) throws CADoesntExistsException, AuthorizationDeniedException;

    /**
     * Method that is used to create a new CA from an imported keystore from
     * another type of CA, for example OpenSSL.
     * 
     * @param admin Administrator
     * @param caname
     *            the CA-name (human readable) the newly created CA will get
     * @param p12file a byte array of old server p12 file.
     * @param keystorepass used to unlock the keystore.
     * @param privkeypass used to unlock the private key.
     * @param privateSignatureKeyAlias
     *            the alias for the private key in the keystore.
     * @param privateEncryptionKeyAlias
     *            the alias for the private encryption key in the keystore
     */
    public void importCAFromKeyStore(AuthenticationToken admin, String caname, byte[] p12file, String keystorepass, String privkeypass, String privateSignatureKeyAlias,
            String privateEncryptionKeyAlias);

    /**
     * Method that is used to create a new CA from keys and certificates.
     * 
     * @param caname The name the new CA will have
     * @param keystorepass The keystore password the CA will have
     * @param signatureCertChain The CA certificate(s)
     * @param p12PublicSignatureKey CA public signature key
     * @param p12PrivateSignatureKey CA private signature key
     * @param p12PrivateEncryptionKey
     *            CA private encryption key, or null to generate a new
     *            encryption key
     * @param p12PublicEncryptionKey
     *            CA public encryption key, or null to generate a new
     *            encryption key
     * @throws CAOfflineException if CRLs can not be generated because imported CA did not manage to get online
     */
    public void importCAFromKeys(AuthenticationToken admin, String caname, String keystorepass, java.security.cert.Certificate[] signatureCertChain,
            java.security.PublicKey p12PublicSignatureKey, java.security.PrivateKey p12PrivateSignatureKey, java.security.PrivateKey p12PrivateEncryptionKey,
            java.security.PublicKey p12PublicEncryptionKey) throws CryptoTokenAuthenticationFailedException, CryptoTokenOfflineException, IllegalCryptoTokenException, CADoesntExistsException, CAExistsException, AuthorizationDeniedException, CAOfflineException;

    /**
     * Method that is used to create a new CA from keys on an HSM and
     * certificates in a file.
     * 
     * @param admin
     *            Administrator
     * @param caname
     *            the CA-name (human readable) the newly created CA will get
     * @param signatureCertChain
     *            chain of certificates, this CAs certificate first.
     * @param catokenpassword
     *            used to unlock the HSM keys.
     * @param catokenclasspath
     *            classpath to one of the HardToken classes, for example
     *            org.cesecore.keys.token.PKCS11CryptoToken.
     * @param catokenproperties
     *            the catoken properties, same as usually entered in the
     *            adminGUI for hard token CAs.
     * @throws CAOfflineException if CRLs can not be generated because imported CA did not manage to get online
     * @throws NoSuchSlotException if no slot as defined by the label in catokenproperties could be found
     */
    public void importCAFromHSM(AuthenticationToken admin, String caname, Certificate[] signatureCertChain, String catokenpassword,
            String catokenclasspath, String catokenproperties) throws CryptoTokenOfflineException, CryptoTokenAuthenticationFailedException, IllegalCryptoTokenException, CADoesntExistsException, CAExistsException, AuthorizationDeniedException, CAOfflineException, NoSuchSlotException;

    /**
     * Exports a CA to file. The method only works for soft tokens.
     * 
     * @param admin Administrator
     * @param caname the CA-name (human readable) the CA
     * @param keystorepass used to lock the keystore.
     * @param privkeypass used to lock the private key.
     * @param privateSignatureKeyAlias
     *            the alias for the private signature key in the keystore.
     * @param privateEncryptionKeyAlias
     *            the alias for the private encryption key in teh keystore
     * @return A byte array of the CAs p12 in case of X509 CA and pkcs8 private
     *         certificate signing key in case of CVC CA.
     */
    public byte[] exportCAKeyStore(AuthenticationToken admin, String caname, String keystorepass, String privkeypass, String privateSignatureKeyAlias,
            String privateEncryptionKeyAlias) throws Exception;

    /**
     * Method returning a Collection of Certificate of all CA certificates known
     * to the system. Certificates for External CAs or CAs that are awaiting
     * certificate response are not returned, because we don't have certificates
     * for them. Uses getAvailableCAs to list CAs.
     */
    public Collection<Certificate> getAllCACertificates();

    /**
     * Activates an 'Offline' CA Token and sets the CA status to active and
     * ready for use again. The admin must be authorized to
     * "/ca_functionality/basic_functions/activate_ca" in order to
     * activate/deactivate.
     * 
     * @param admin the administrator calling the method
     * @param caid the is of the CA to activate
     * @param authorizationcode the authorization code used to unlock the CA tokens private keys.
     * @throws AuthorizationDeniedException
     *             it the administrator isn't authorized to activate the CA.
     * @throws ApprovalException
     *             if an approval already is waiting for specified action
     * @throws WaitingForApprovalException
     *             if approval is required and the action have been added in the
     *             approval queue.
     */
    public void activateCAService(AuthenticationToken admin, int caid) throws AuthorizationDeniedException,
        ApprovalException, WaitingForApprovalException, CADoesntExistsException;

    /**
     * Deactivates an 'active' CA token and sets the CA status to offline. The
     * admin must be authorized to
     * "/ca_functionality/basic_functions/activate_ca" in order to
     * activate/deactivate.
     * 
     * @param admin the administrator calling the method
     * @param caid the is of the CA to activate.
     * @throws AuthorizationDeniedException
     *             it the administrator isn't authorized to activate the CA.
     * @throws EjbcaException
     *             if the given caid couldn't be found or its status is wrong.
     */
    public void deactivateCAService(AuthenticationToken admin, int caid) throws AuthorizationDeniedException, CADoesntExistsException;

    /**
     * Removes the catoken keystore from the database and sets its status to
     * {@link CryptoToken#STATUS_OFFLINE}.
     * 
     * The signature algorithm, encryption algorithm, key algorithm and other
     * properties are not removed so that the keystore can later by restored by
     * using

     * {@link CAAdminSession#restoreCAKeyStore(AuthenticationToken, String, byte[], String, String, String, String)}
     * .
     * 
     * 
     * @param admin Administrator
     * @param caname Name (human readable) of CA for which the keystore should be removed
     * @throws EJBException in case if the catoken is not a soft catoken
     * 

     * @see CAAdminSession#exportCAKeyStore(AuthenticationToken, String, String, String, String, String)
     */
    public void removeCAKeyStore(AuthenticationToken admin, String caname) throws EJBException;

    /**
     * Restores the keys for the CAToken from a KeyStore.
     * 
     * @param admin Administrator
     * @param caname
     *            Name (human readable) of the CA for which the keystore should
     *            be restored
     * @param p12file The KeyStore to read keys from
     * @param keystorepass Password for the KeyStore
     * @param privkeypass Password for the private key
     * @param privateSignatureKeyAlias
     *            Alias of the signature key in the KeyStore
     * @param privateEncryptionKeyAlias
     *            Alias of the encryption key in the KeyStore
     * @throws EJBException
     *             in case of the CAToken is not a soft CAToken or if the CA
     *             already has an active CAToken or if any of the aliases can
     *             not be found or if the KeyStore does not contain the right
     *             private key
     */
    public void restoreCAKeyStore(AuthenticationToken admin, String caname, byte[] p12file, String keystorepass, String privkeypass, String privateSignatureKeyAlias,
            String privateEncryptionKeyAlias) throws EJBException;

    /**
     * Method used to edit the data of a CA.
     * 
     * Not all of the CAs data can be edited after the creation, therefore will
     * only the values from CAInfo that is possible be updated.
     * 
     * @param cainfo CAInfo object containing values that will be updated
     * 
     * @see org.ejbca.core.model.ca.caadmin.CAInfo
     * @see org.ejbca.core.model.ca.caadmin.X509CAInfo
     */
    public void editCA(AuthenticationToken admin, CAInfo cainfo) throws AuthorizationDeniedException;
    
    /**
     * Method used to check if certificate profile id exists in any CA.
     * 
     * @param admin The admin performing the action 
     * @param certificateprofileid the ID of the sought certificate profile
     * @return a list of names of the CAs using the certificate profile 
     */
    public List<String> getCAsUsingCertificateProfile(int certificateprofileid);

    /**
     * Method used to check if publishers id exists in any CAs CRLPublishers
     * Collection.
     */
    public boolean exitsPublisherInCAs(AuthenticationToken admin, int publisherid);

    /**
     * Method that publishes the given CA certificate chain to the list of
     * publishers. Is mainly used when CA is created.
     * 
     * @param admin Information about the administrator performing the event.
     * @param certificatechain Certificate chain to publish
     * @param usedpublishers
     *            a collection if publisher id's (Integer) indicating which
     *            publisher that should be used.
     * @param caDataDN
     *            DN from CA data. If a the CA certificate does not have a DN
     *            object to be used by the publisher this DN could be searched
     *            for the object.
     */
    public void publishCACertificate(AuthenticationToken admin, Collection<Certificate> certificatechain, Collection<Integer> usedpublishers, String caDataDN) throws AuthorizationDeniedException;

    /**
     * (Re-)Publish the last CRLs for a CA.
     *
     * @param admin            Information about the administrator performing the event.
     * @param caCert           The certificate for the CA to publish CRLs for
     * @param usedpublishers   a collection if publisher id's (Integer) indicating which publisher that should be used.
     * @param caDataDN         DN from CA data. If a the CA certificate does not have a DN object to be used by the publisher this DN could be searched for the object.
     * @param doPublishDeltaCRL should delta CRLs be published?
     * @throws AuthorizationDeniedException 
     */
    public void publishCRL(AuthenticationToken admin, Certificate caCert, Collection<Integer> usedpublishers, String caDataDN, boolean doPublishDeltaCRL) throws AuthorizationDeniedException;

    /** @return a Collection of id:s (Integer) to authorized publishers. */
    public Collection<Integer> getAuthorizedPublisherIds(AuthenticationToken admin);

    /**
     * Method used to create a new CA.
     * 
     * The cainfo parameter should at least contain the following information.
     * SubjectDN Name (if null then is subjectDN used).
     * Description (optional) Status (SecConst.CA_ACTIVE or
     * SecConst.CA_WAITING_CERTIFICATE_RESPONSE) SignedBy (CAInfo.SELFSIGNED,
     * CAInfo.SIGNEDBYEXTERNALCA or CAId of internal CA)
     * 
     * @throws InvalidAlgorithmException if the CA signature algorithm is invalid
     * 
     * For other optional values see:
     * @see org.ejbca.core.model.ca.caadmin.CAInfo
     * @see org.ejbca.core.model.ca.caadmin.X509CAInfo
     */
    public void createCA(AuthenticationToken admin, CAInfo cainfo) throws CAExistsException, AuthorizationDeniedException, CryptoTokenOfflineException, CryptoTokenAuthenticationFailedException, InvalidAlgorithmException;

    /**
     * Method used to perform a extended CA Service, like OCSP CA Service.
     *
     * @param admin   Information about the administrator or admin performing the event.
     * @param caid    the CA that should perform the service
     * @param request a service request.
     * @return A corresponding response.
     * @throws IllegalExtendedCAServiceRequestException
     *                                 if the request was invalid.
     * @throws ExtendedCAServiceNotActiveException
     *                                 thrown when the service for the given CA isn't activated
     * @throws CADoesntExistsException The given caid does not exist.
     * @throws OperatorCreationException 
     * @throws CertificateException 
     * @throws CertificateEncodingException 
     */
    public ExtendedCAServiceResponse extendedService(AuthenticationToken admin, int caid,
            ExtendedCAServiceRequest request)
            throws ExtendedCAServiceRequestException,
            IllegalExtendedCAServiceRequestException,
            ExtendedCAServiceNotActiveException, CADoesntExistsException, AuthorizationDeniedException, CertificateEncodingException, CertificateException, OperatorCreationException;

    /**
     * Makes sure that no CAs are cached to ensure that we read from database
     * next time we try to access it.
     * Present in remote interface so we can call it from CLI.
     */
    public void flushCACache();

    /** @return the latest link certificate (if any) */
    byte[] getLatestLinkCertificate(int caId) throws CADoesntExistsException;
}
