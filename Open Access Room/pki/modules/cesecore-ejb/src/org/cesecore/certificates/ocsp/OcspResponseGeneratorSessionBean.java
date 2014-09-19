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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.RevokedInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.jcajce.JcaRespID;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.cesecore.authentication.tokens.AlwaysAllowLocalAuthenticationToken;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CAConstants;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionLocal;
import org.cesecore.certificates.ca.InvalidAlgorithmException;
import org.cesecore.certificates.ca.SignRequestException;
import org.cesecore.certificates.ca.SignRequestSignatureException;
import org.cesecore.certificates.ca.catoken.CAToken;
import org.cesecore.certificates.ca.catoken.CATokenConstants;
import org.cesecore.certificates.certificate.CertificateStatus;
import org.cesecore.certificates.certificate.CertificateStoreSessionLocal;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.ocsp.cache.OcspConfigurationCache;
import org.cesecore.certificates.ocsp.cache.OcspExtensionsCache;
import org.cesecore.certificates.ocsp.cache.OcspSigningCache;
import org.cesecore.certificates.ocsp.cache.OcspSigningCacheEntry;
import org.cesecore.certificates.ocsp.exception.CryptoProviderException;
import org.cesecore.certificates.ocsp.exception.MalformedRequestException;
import org.cesecore.certificates.ocsp.exception.NotSupportedException;
import org.cesecore.certificates.ocsp.exception.OcspFailureException;
import org.cesecore.certificates.ocsp.extension.OCSPExtension;
import org.cesecore.certificates.ocsp.keys.CardKeys;
import org.cesecore.certificates.ocsp.logging.AuditLogger;
import org.cesecore.certificates.ocsp.logging.PatternLogger;
import org.cesecore.certificates.ocsp.logging.TransactionLogger;
import org.cesecore.certificates.util.AlgorithmTools;
import org.cesecore.config.ConfigurationHolder;
import org.cesecore.config.OcspConfiguration;
import org.cesecore.internal.InternalResources;
import org.cesecore.jndi.JndiConstants;
import org.cesecore.keybind.CertificateImportException;
import org.cesecore.keybind.InternalKeyBinding;
import org.cesecore.keybind.InternalKeyBindingDataSessionLocal;
import org.cesecore.keybind.InternalKeyBindingInfo;
import org.cesecore.keybind.InternalKeyBindingMgmtSessionLocal;
import org.cesecore.keybind.InternalKeyBindingNameInUseException;
import org.cesecore.keybind.InternalKeyBindingStatus;
import org.cesecore.keybind.InternalKeyBindingTrustEntry;
import org.cesecore.keybind.impl.AuthenticationKeyBinding;
import org.cesecore.keybind.impl.OcspKeyBinding;
import org.cesecore.keybind.impl.OcspKeyBinding.ResponderIdType;
import org.cesecore.keys.token.BaseCryptoToken;
import org.cesecore.keys.token.CryptoToken;
import org.cesecore.keys.token.CryptoTokenManagementSessionLocal;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.cesecore.keys.token.CryptoTokenSessionLocal;
import org.cesecore.keys.token.PKCS11CryptoToken;
import org.cesecore.keys.token.SoftCryptoToken;
import org.cesecore.keys.token.p11.Pkcs11SlotLabelType;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.util.CertTools;
import org.cesecore.util.log.ProbableErrorHandler;
import org.cesecore.util.log.SaferAppenderListener;
import org.cesecore.util.log.SaferDailyRollingFileAppender;

/**
 * This SSB generates OCSP responses. 
 * 
 * @version $Id: OcspResponseGeneratorSessionBean.java 18264 2013-12-10 18:01:49Z mikekushner $
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "OcspResponseGeneratorSessionRemote")
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class OcspResponseGeneratorSessionBean implements OcspResponseGeneratorSessionRemote, OcspResponseGeneratorSessionLocal, SaferAppenderListener {

    /** Max size of a request is 100000 bytes */
    private static final int MAX_REQUEST_SIZE = 100000;

    private static final String hardTokenClassName = OcspConfiguration.getHardTokenClassName();

    private static final Logger log = Logger.getLogger(OcspResponseGeneratorSessionBean.class);

    private static final InternalResources intres = InternalResources.getInstance();
    
    private static volatile ExecutorService service = Executors.newCachedThreadPool();
    
    @Resource
    private SessionContext sessionContext;
    /* When the sessionContext is injected, the timerService should be looked up.
     * This is due to the Glassfish EJB verifier complaining. 
     */
    private TimerService timerService;

    @EJB
    private CaSessionLocal caSession;
    @EJB
    private CertificateStoreSessionLocal certificateStoreSession;
    @EJB
    private CryptoTokenSessionLocal cryptoTokenSession;
    @EJB
    private CryptoTokenManagementSessionLocal cryptoTokenManagementSession;
    @EJB
    private InternalKeyBindingDataSessionLocal internalKeyBindingDataSession;
    @EJB
    private InternalKeyBindingMgmtSessionLocal internalKeyBindingMgmtSession;

    private JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter();

    @PostConstruct
    public void init() throws AuthorizationDeniedException {
        if (OcspConfiguration.getLogSafer() == true) {
            SaferDailyRollingFileAppender.addSubscriber(this);
            log.info("Added us as subscriber: " + SaferDailyRollingFileAppender.class.getCanonicalName());
        }
        timerService = sessionContext.getTimerService();
    }
    
    @Override
    public void initTimers() {
        // Reload OCSP signing cache, and cancel/create timers if there are no timers or if the cache is empty (probably a fresh startup)
        if ((timerService.getTimers().size() == 0) || (OcspSigningCache.INSTANCE.getEntries().isEmpty())){
            reloadOcspSigningCache();
        } else {
            log.info("Not initing OCSP reload timers, there are already some.");
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void reloadOcspSigningCache() {
    	if (log.isTraceEnabled()) {
    		log.trace(">reloadOcspSigningCache");
    	}
    	
        // Cancel any waiting timers
        cancelTimers();
        try {      
         // Verify card key holder
            if (log.isDebugEnabled() && (CardKeyHolder.getInstance().getCardKeys() == null)) {
                log.debug(intres.getLocalizedMessage("ocsp.classnotfound", hardTokenClassName));
            }
            // Populate OcspSigningCache
            try {
                OcspSigningCache.INSTANCE.stagingStart();
                // Add all potential CA's as OCSP responders to the staging area
                for (final Integer caId : caSession.getAvailableCAs()) {
                    final List<X509Certificate> caCertificateChain = new ArrayList<X509Certificate>();
                    try {
                        final CAInfo caInfo = caSession.getCAInfoInternal(caId.intValue());
                        if (caInfo.getCAType() == CAInfo.CATYPE_CVC || caInfo.getStatus() != CAConstants.CA_ACTIVE) {
                            // Bravely ignore OCSP for CVC CAs or CA's that have no CA certificate (yet)
                            continue;
                        } 
                        if (log.isDebugEnabled()) {
                            log.debug("Processing X509 CA " + caInfo.getName() + " (" + caInfo.getCAId() + ").");
                        }
                        final CAToken caToken = caInfo.getCAToken();
                        final CryptoToken cryptoToken = cryptoTokenSession.getCryptoToken(caToken.getCryptoTokenId());
                        if (cryptoToken == null) {
                            log.info("Excluding CA with id " + caId + " for OCSP signing consideration due to missing CryptoToken.");
                            continue;
                        }
                        for (final Certificate certificate : caInfo.getCertificateChain()) {
                            caCertificateChain.add((X509Certificate) certificate);
                        }
                        final String keyPairAlias;
                        try {
                            keyPairAlias = caToken.getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN);
                        } catch (CryptoTokenOfflineException e) {
                            log.warn("Referenced private key with purpose " + CATokenConstants.CAKEYPURPOSE_CERTSIGN + " could not be used. CryptoToken is off-line for CA with id "+caId+": " + e.getMessage());
                            continue;
                        }
                        final PrivateKey privateKey;
                        try {
                            privateKey = cryptoToken.getPrivateKey(keyPairAlias);
                        } catch (CryptoTokenOfflineException e) {
                            log.warn("Referenced private key with alias " + keyPairAlias + " could not be used. CryptoToken is off-line for CA with id "+caId+": " + e.getMessage());
                            continue;
                        }
                        if (privateKey == null) {
                            log.warn("Referenced private key with alias " + keyPairAlias + " does not exist. Ignoring CA with id "+caId);
                            continue;
                        }
                        final String signatureProviderName = cryptoToken.getSignProviderName();
                        OcspSigningCache.INSTANCE.stagingAdd(new OcspSigningCacheEntry(caCertificateChain, null, privateKey, signatureProviderName, null));
                    } catch (CADoesntExistsException e) {
                        // Should only happen if the CA was deleted between the getAvailableCAs and the last one
                        log.warn("CA with Id " + caId + " disappeared during reload operation.");
                    }
                }
                // Add all potential InternalKeyBindings as OCSP responders to the staging area, overwriting CA entries from before
                for (final int internalKeyBindingId : internalKeyBindingDataSession.getIds(OcspKeyBinding.IMPLEMENTATION_ALIAS)) {
                    final OcspKeyBinding ocspKeyBinding = (OcspKeyBinding) internalKeyBindingDataSession.getInternalKeyBinding(internalKeyBindingId);  
                    if (log.isDebugEnabled()) {
                        log.debug("Processing " + ocspKeyBinding.getName() + " (" + ocspKeyBinding.getId() + ")");
                    }
                    if (!ocspKeyBinding.getStatus().equals(InternalKeyBindingStatus.ACTIVE)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Ignoring OcspKeyBinding since it is not active.");
                        }
                        continue;
                    }
                    final X509Certificate ocspSigningCertificate = (X509Certificate) certificateStoreSession.findCertificateByFingerprint(ocspKeyBinding.getCertificateId());
                    if (ocspSigningCertificate == null) {
                        log.warn("OCSP signing certificate with referenced fingerprint " + ocspKeyBinding.getCertificateId() +
                                " does not exist. Ignoring internalKeyBinding with id " + ocspKeyBinding.getId());
                        continue;
                    }
                    OcspSigningCacheEntry ocspSigningCacheEntry = makeOcspSigningCacheEntry(ocspSigningCertificate, ocspKeyBinding);
                    if (ocspSigningCacheEntry == null) {
                        continue;
                    } else {
                        OcspSigningCache.INSTANCE.stagingAdd(ocspSigningCacheEntry);
                    }
                }
                OcspSigningCache.INSTANCE.stagingCommit();
            } finally {
                OcspSigningCache.INSTANCE.stagingRelease();
            }
            
        } finally {
            // Schedule a new timer
            addTimer(OcspConfiguration.getSigningCertsValidTimeInMilliseconds(), OcspSigningCache.INSTANCE.hashCode());
            
        }
    }

    /**
     * Constructs an OcspSigningCacheEntry from the given parameters.
     * 
     * @param ocspSigningCertificate The signing certificate associated with the key binding. May be found separately, so given as a separate parameter
     * @param ocspKeyBinding the Key Binding to base the cache entry off of. 
     * @return an OcspSigningCacheEntry, or null if any error was encountered.
     */
    private OcspSigningCacheEntry makeOcspSigningCacheEntry(X509Certificate ocspSigningCertificate, OcspKeyBinding ocspKeyBinding) {
        final List<X509Certificate> caCertificateChain = getCaCertificateChain(ocspSigningCertificate);
        if (caCertificateChain == null) {
            log.warn("OcspKeyBinding " + ocspKeyBinding.getName() + " ( " + ocspKeyBinding.getId() + ") has an signing certificate, but no chain and will be ignored.");
            return null;
        }
        final CryptoToken cryptoToken = cryptoTokenSession.getCryptoToken(ocspKeyBinding.getCryptoTokenId());
        if (cryptoToken == null) {
            log.warn("Referenced CryptoToken with id " + ocspKeyBinding.getCryptoTokenId() + " does not exist. Ignoring OcspKeyBinding with id "
                    + ocspKeyBinding.getId());
            return null;
        }
        final PrivateKey privateKey;
        try {
            privateKey = cryptoToken.getPrivateKey(ocspKeyBinding.getKeyPairAlias());
        } catch (CryptoTokenOfflineException e) {
            log.warn("Referenced private key with alias " + ocspKeyBinding.getKeyPairAlias() + " could not be used. CryptoToken is off-line for OcspKeyBinding with id "+ocspKeyBinding.getId()+": " + e.getMessage());
            return null;
        }
        if (privateKey == null) {
            log.warn("Referenced private key with alias " + ocspKeyBinding.getKeyPairAlias() + " does not exist. Ignoring OcspKeyBinding with id "+ ocspKeyBinding.getId());
            return null;
        }
        final String signatureProviderName = cryptoToken.getSignProviderName();
        if (log.isDebugEnabled()) {
            log.debug("Adding OcspKeyBinding "+ocspKeyBinding.getId()+", "+ocspKeyBinding.getName());
        }
        return new OcspSigningCacheEntry(caCertificateChain, ocspSigningCertificate, privateKey, signatureProviderName, ocspKeyBinding);
    }
    
    private List<X509Certificate> getCaCertificateChain(final X509Certificate leafCertificate) {
        final List<X509Certificate> caCertificateChain = new ArrayList<X509Certificate>();
        X509Certificate currentLevelCertificate = leafCertificate;
        while (!CertTools.getIssuerDN(currentLevelCertificate).equals(CertTools.getSubjectDN(currentLevelCertificate))) {
            final String issuerDn = CertTools.getIssuerDN(currentLevelCertificate);
            currentLevelCertificate = certificateStoreSession.findLatestX509CertificateBySubject(issuerDn);
            if (currentLevelCertificate == null) {
                log.warn("Unable to build certificate chain for OCSP signing certificate with Subject DN '" +
                        CertTools.getSubjectDN(leafCertificate) + "'. CA with Subject DN '" + issuerDn + "' is missing in the database.");
                return null;
            }
            caCertificateChain.add(currentLevelCertificate);
        }
        return caCertificateChain;
    }
   
    
    @Override
    public void setCanlog(boolean canLog) {
        CanLogCache.INSTANCE.setCanLog(canLog);
    }

    /**
     * This method exists solely to avoid code duplication when error handling in getOcspResponse.
     * 
     * @param responseGenerator A OCSPRespBuilder for generating a response with state INTERNAL_ERROR.
     * @param transactionLogger The TransactionLogger for this call.
     * @param auditLogger The AuditLogger for this call.
     * @param e The thrown exception.
     * @return a response with state INTERNAL_ERROR.
     * @throws OCSPException if generation of the response failed.
     */
    private OCSPResp processDefaultError(OCSPRespBuilder responseGenerator, TransactionLogger transactionLogger, AuditLogger auditLogger, Throwable e)
            throws OCSPException {
        transactionLogger.paramPut(PatternLogger.PROCESS_TIME, PatternLogger.PROCESS_TIME);
        auditLogger.paramPut(PatternLogger.PROCESS_TIME, PatternLogger.PROCESS_TIME);
        String errMsg = intres.getLocalizedMessage("ocsp.errorprocessreq", e.getMessage());
        log.error(errMsg, e);

        transactionLogger.paramPut(TransactionLogger.STATUS, OCSPRespBuilder.INTERNAL_ERROR);
        transactionLogger.writeln();
        auditLogger.paramPut(AuditLogger.STATUS, OCSPRespBuilder.INTERNAL_ERROR);
        return responseGenerator.build(OCSPRespBuilder.INTERNAL_ERROR, null); // RFC 2560: responseBytes are not set on error.
    }

    private BasicOCSPResp signOcspResponse(OCSPReq req, List<OCSPResponseItem> responseList, Extensions exts, 
            final OcspSigningCacheEntry ocspSigningCacheEntry, Date producedAt) throws CryptoTokenOfflineException {
        final X509Certificate[] certChain = ocspSigningCacheEntry.getFullCertificateChain().toArray(new X509Certificate[0]);
        final X509Certificate signerCert = certChain[0];
        if(!CertTools.isOCSPCert(signerCert) && ocspSigningCacheEntry.isUsingSeparateOcspSigningCertificate()) {
            log.warn("Signing with non OCSP certificate (no 'OCSP Signing' Extended Key Usage) bound by OcspKeyBinding '" + ocspSigningCacheEntry.getOcspKeyBinding().getName() + "'.");
        }
        final String sigAlg;
        if (ocspSigningCacheEntry.isUsingSeparateOcspSigningCertificate()) {
            // If we have an OcspKeyBinding we use this configuration to override the default
            sigAlg = ocspSigningCacheEntry.getOcspKeyBinding().getSignatureAlgorithm();
        } else {
            final String sigAlgs = OcspConfiguration.getSignatureAlgorithm();
            final PublicKey pk = signerCert.getPublicKey();
            sigAlg = getSigningAlgFromAlgSelection(sigAlgs, pk);
        }
        if (log.isDebugEnabled()) {
            log.debug("Signing algorithm: " + sigAlg);
        }
        boolean includeChain = OcspConfiguration.getIncludeCertChain();
        // If we have an OcspKeyBinding we use this configuration to override the default
        if (ocspSigningCacheEntry.isUsingSeparateOcspSigningCertificate()) {
            includeChain = ocspSigningCacheEntry.getOcspKeyBinding().getIncludeCertChain();
        }
        if (log.isDebugEnabled()) {
            log.debug("Include chain: " + includeChain);
        }
        final X509Certificate[] chain;
        if (includeChain) {
            chain = certChain;
        } else {
            chain = new X509Certificate[1];
            chain[0] = signerCert;
        }
        try {
            int respIdType = OcspConfiguration.getResponderIdType();
            // If we have an OcspKeyBinding we use this configuration to override the default
            if (ocspSigningCacheEntry.isUsingSeparateOcspSigningCertificate()) {
                if (ResponderIdType.NAME.equals(ocspSigningCacheEntry.getOcspKeyBinding().getResponderIdType())) {
                    respIdType = OcspConfiguration.RESPONDERIDTYPE_NAME;
                } else {
                    respIdType = OcspConfiguration.RESPONDERIDTYPE_KEYHASH;
                }
            }
            
            // Now we can use the returned OCSPServiceResponse to get private key and certificate chain to sign the ocsp response
            final BasicOCSPResp ocspresp = generateBasicOcspResp(req, exts, responseList, sigAlg, signerCert, ocspSigningCacheEntry.getPrivateKey(),
                    ocspSigningCacheEntry.getSignatureProviderName(), chain, respIdType, producedAt);

            if (log.isDebugEnabled()) {
                Collection<X509Certificate> coll = Arrays.asList(chain);
                log.debug("Cert chain for OCSP signing is of size " + coll.size());
            }

            if (CertTools.isCertificateValid(signerCert)) {
                return ocspresp;
            } else {
                throw new OcspFailureException("Response was not validly signed.");
            }
        } catch (OCSPException ocspe) {
            throw new OcspFailureException(ocspe);
        } catch (NoSuchProviderException nspe) {
            throw new OcspFailureException(nspe);
        } catch (NotSupportedException e) {
            log.info("OCSP Request type not supported: ", e);
            throw new OcspFailureException(e);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException: ", e);
            throw new OcspFailureException(e);
        }
    }

    /**
     * This method takes byte array and translates it onto a OCSPReq class.
     * 
     * @param request the byte array in question.
     * @param remoteAddress The remote address of the HttpRequest associated with this array.
     * @param transactionLogger A transaction logger.
     * @return
     * @throws MalformedRequestException
     * @throws SignRequestException thrown if an unsigned request was processed when system configuration requires that all requests be signed.
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws SignRequestSignatureException
     */
    private OCSPReq translateRequestFromByteArray(byte[] request, String remoteAddress, TransactionLogger transactionLogger)
            throws MalformedRequestException, SignRequestException, SignRequestSignatureException, CertificateException, NoSuchAlgorithmException {

        OCSPReq ocspRequest = null;
        try {
            ocspRequest = new OCSPReq(request);
        } catch (IOException e) {
            throw new MalformedRequestException("Could not form OCSP request", e);
        }

        if (ocspRequest.getRequestorName() == null) {
            if (log.isDebugEnabled()) {
                log.debug("Requestor name is null");
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Requestor name is: " + ocspRequest.getRequestorName().toString());
            }
            transactionLogger.paramPut(TransactionLogger.REQ_NAME, ocspRequest.getRequestorName().toString());
        }

        /**
         * check the signature if contained in request. if the request does not contain a signature and the servlet is configured in the way the a
         * signature is required we send back 'sigRequired' response.
         */
        if (log.isDebugEnabled()) {
            log.debug("Incoming OCSP request is signed : " + ocspRequest.isSigned());
        }
        if (ocspRequest.isSigned()) {
            X509Certificate signercert = checkRequestSignature(remoteAddress, ocspRequest);
            String signercertIssuerName = CertTools.getIssuerDN(signercert);
            BigInteger signercertSerNo = CertTools.getSerialNumber(signercert);
            String signercertSubjectName = CertTools.getSubjectDN(signercert);

            transactionLogger.paramPut(TransactionLogger.SIGN_ISSUER_NAME_DN, signercertIssuerName);
            transactionLogger.paramPut(TransactionLogger.SIGN_SERIAL_NO, signercert.getSerialNumber().toByteArray());
            transactionLogger.paramPut(TransactionLogger.SIGN_SUBJECT_NAME, signercertSubjectName);
            transactionLogger.paramPut(PatternLogger.REPLY_TIME, TransactionLogger.REPLY_TIME);
            // Check if we have configured request verification using the old property file way..
            if (OcspConfiguration.getEnforceRequestSigning()) {
                // If it verifies OK, check if it is revoked
                final CertificateStatus status = certificateStoreSession.getStatus(CertTools.getIssuerDN(signercert),
                        CertTools.getSerialNumber(signercert));
                /*
                 * If rci == null it means the certificate does not exist in database, we then treat it as ok, because it may be so that only revoked
                 * certificates is in the (external) OCSP database.
                 */
                if (status.equals(CertificateStatus.REVOKED)) {
                    String serno = signercertSerNo.toString(16);
                    String infoMsg = intres.getLocalizedMessage("ocsp.infosigner.revoked", signercertSubjectName, signercertIssuerName, serno);
                    log.info(infoMsg);
                    throw new SignRequestSignatureException(infoMsg);
                }
            }
            // Next, check if there is an OcspKeyBinding where signing is required and configured for this request
            // In the case where multiple requests are bundled together they all must be trusting the signer
            for (final Req req : ocspRequest.getRequestList()) {
                OcspSigningCacheEntry ocspSigningCacheEntry = OcspSigningCache.INSTANCE.getEntry(req.getCertID());
                if (ocspSigningCacheEntry==null) {
                    if (log.isTraceEnabled()) {
                        log.trace("Using default responder to check signature.");
                    }
                    ocspSigningCacheEntry = OcspSigningCache.INSTANCE.getDefaultEntry();
                }   
                if (ocspSigningCacheEntry!=null && ocspSigningCacheEntry.isUsingSeparateOcspSigningCertificate()) {
                    if (log.isTraceEnabled()) {
                        log.trace("ocspSigningCacheEntry.isUsingSeparateOcspSigningCertificate: " + ocspSigningCacheEntry.isUsingSeparateOcspSigningCertificate());
                    }
                    final OcspKeyBinding ocspKeyBinding = ocspSigningCacheEntry.getOcspKeyBinding();
                    if (log.isTraceEnabled()) {
                        log.trace("OcspKeyBinding " + ocspKeyBinding.getId() + ", RequireTrustedSignature: " + ocspKeyBinding.getRequireTrustedSignature());
                    }
                    if (ocspKeyBinding.getRequireTrustedSignature()) {
                        boolean isTrusted = false;
                        final List<InternalKeyBindingTrustEntry> trustedCertificateReferences = ocspKeyBinding.getTrustedCertificateReferences();
                        if (trustedCertificateReferences.isEmpty()) {
                            // We trust ANY cert from a known CA
                            isTrusted = true;
                        } else {
                            for (final InternalKeyBindingTrustEntry trustEntry : trustedCertificateReferences) {
                                final int trustedCaId = trustEntry.getCaId();
                                final BigInteger trustedSerialNumber = trustEntry.getCertificateSerialNumber();
                                if (log.isTraceEnabled()) {
                                    log.trace("Processing trustedCaId="+trustedCaId + " trustedSerialNumber="+trustedSerialNumber + " signercertIssuerName.hashCode()="+
                                            signercertIssuerName.hashCode()+" signercertSerNo="+signercertSerNo);
                                }
                                if (trustedCaId == signercertIssuerName.hashCode()) {
                                    if (trustedSerialNumber == null) {
                                        // We trust any certificate from this CA
                                        isTrusted = true;
                                        if (log.isTraceEnabled()) {
                                            log.trace("Trusting request signature since ANY certificate from issuer "+trustedCaId+" is trusted.");
                                        }
                                        break;
                                    } else if (signercertSerNo.equals(trustedSerialNumber)) {
                                        // We trust this particular certificate from this CA
                                        isTrusted = true;
                                        if (log.isTraceEnabled()) {
                                            log.trace("Trusting request signature since certificate with serialnumber " + trustedSerialNumber + " from issuer "+trustedCaId+" is trusted.");
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                        if (!isTrusted) {
                            final String infoMsg = intres.getLocalizedMessage("ocsp.infosigner.notallowed", signercertSubjectName, signercertIssuerName,
                                    signercertSerNo.toString(16));
                            log.info(infoMsg);
                            throw new SignRequestSignatureException(infoMsg);
                        }
                    }
                }
            }
        } else {
            if (OcspConfiguration.getEnforceRequestSigning()) {
                // Signature required
                throw new SignRequestException("Signature required");
            }
            // Next, check if there is an OcspKeyBinding where signing is required and configured for this request
            // In the case where multiple requests are bundled together they all must be trusting the signer
            for (final Req req : ocspRequest.getRequestList()) {
                OcspSigningCacheEntry ocspSigningCacheEntry = OcspSigningCache.INSTANCE.getEntry(req.getCertID());
                if (ocspSigningCacheEntry==null) {
                    ocspSigningCacheEntry = OcspSigningCache.INSTANCE.getDefaultEntry();
                }
                if (ocspSigningCacheEntry != null && ocspSigningCacheEntry.isUsingSeparateOcspSigningCertificate()) {
                    final OcspKeyBinding ocspKeyBinding = ocspSigningCacheEntry.getOcspKeyBinding();
                    if (ocspKeyBinding.getRequireTrustedSignature()) {
                        throw new SignRequestException("Signature required");
                    }
                }
            }
        }
        return ocspRequest;
    }

    /**
     * Checks the signature on an OCSP request. Does not check for revocation of the signer certificate
     * 
     * @param clientRemoteAddr The IP address or host name of the remote client that sent the request, can be null.
     * @param req The signed OCSPReq
     * @return X509Certificate which is the certificate that signed the OCSP request
     * @throws SignRequestSignatureException if signature verification fail, or if the signing certificate is not authorized
     * @throws SignRequestException if there is no signature on the OCSPReq
     * @throws OCSPException if the request can not be parsed to retrieve certificates
     * @throws NoSuchProviderException if the BC provider is not installed
     * @throws CertificateException if the certificate can not be parsed
     * @throws NoSuchAlgorithmException if the certificate contains an unsupported algorithm
     * @throws InvalidKeyException if the certificate, or CA key is invalid
     */
    private X509Certificate checkRequestSignature(String clientRemoteAddr, OCSPReq req) throws SignRequestException, SignRequestSignatureException,
            CertificateException, NoSuchAlgorithmException {
        X509Certificate signercert = null;
        if (!req.isSigned()) {
            String infoMsg = intres.getLocalizedMessage("ocsp.errorunsignedreq", clientRemoteAddr);
            log.info(infoMsg);
            throw new SignRequestException(infoMsg);
        }
        // Get all certificates embedded in the request (probably a certificate chain)
        try {
            X509CertificateHolder[] certs = req.getCerts();                
            // Set, as a try, the signer to be the first certificate, so we have a name to log...
            String signer = null;
            if (certs.length > 0) {
                signer = CertTools.getSubjectDN(certificateConverter.getCertificate(certs[0]));
            }
            // We must find a certificate to verify the signature with...
            boolean verifyOK = false;
            for (int i = 0; i < certs.length; i++) {
                X509Certificate certificate = certificateConverter.getCertificate(certs[i]);
                try {
                    if (req.isSignatureValid(new JcaContentVerifierProviderBuilder().build(certificate.getPublicKey()))) {
                        signercert = certificate;
                        signer = CertTools.getSubjectDN(signercert);
                        Date now = new Date();
                        String signerissuer = CertTools.getIssuerDN(signercert);
                        String infoMsg = intres.getLocalizedMessage("ocsp.infosigner", signer);
                        log.info(infoMsg);
                        verifyOK = true;
                        /*
                         * Also check that the signer certificate can be verified by one of the CA-certificates that we answer for
                         */
                        X509Certificate signerca = certificateStoreSession.findLatestX509CertificateBySubject(CertTools.getIssuerDN(certificate));
                        String subject = signer;
                        String issuer = signerissuer;
                        if (signerca != null) {
                            try {
                                signercert.verify(signerca.getPublicKey());
                                if (log.isDebugEnabled()) {
                                    log.debug("Checking validity. Now: " + now + ", signerNotAfter: " + signercert.getNotAfter());
                                }
                                CertTools.checkValidity(signercert, now);
                                // Move the error message string to the CA cert
                                subject = CertTools.getSubjectDN(signerca);
                                issuer = CertTools.getIssuerDN(signerca);
                                CertTools.checkValidity(signerca, now);
                            } catch (SignatureException e) {
                                infoMsg = intres.getLocalizedMessage("ocsp.infosigner.invalidcertsignature", subject, issuer, e.getMessage());
                                log.info(infoMsg);
                                verifyOK = false;
                            } catch (InvalidKeyException e) {
                                infoMsg = intres.getLocalizedMessage("ocsp.infosigner.invalidcertsignature", subject, issuer, e.getMessage());
                                log.info(infoMsg);
                                verifyOK = false;
                            } catch (CertificateNotYetValidException e) {
                                infoMsg = intres.getLocalizedMessage("ocsp.infosigner.certnotyetvalid", subject, issuer, e.getMessage());
                                log.info(infoMsg);
                                verifyOK = false;
                            } catch (CertificateExpiredException e) {
                                infoMsg = intres.getLocalizedMessage("ocsp.infosigner.certexpired", subject, issuer, e.getMessage());
                                log.info(infoMsg);
                                verifyOK = false;
                            }
                        } else {
                            infoMsg = intres.getLocalizedMessage("ocsp.infosigner.nocacert", signer, signerissuer);
                            log.info(infoMsg);
                            verifyOK = false;
                        }
                        break;
                    }
                } catch (OperatorCreationException e) {
                    // Very fatal error
                    throw new EJBException("Can not create Jca content signer: ", e);
                }
            }
            if (!verifyOK) {
                String errMsg = intres.getLocalizedMessage("ocsp.errorinvalidsignature", signer);
                log.info(errMsg);
                throw new SignRequestSignatureException(errMsg);
            }
        } catch (OCSPException e) {
            throw new CryptoProviderException("BouncyCastle was not initialized properly.", e);
        } catch (NoSuchProviderException e) {
            throw new CryptoProviderException("BouncyCastle was not found as a provider.", e);
        }
        return signercert;
    }
    
    private BasicOCSPRespBuilder createOcspResponseGenerator(OCSPReq req, X509Certificate respondercert, int respIdType) throws OCSPException,
            NotSupportedException {
        if (null == req) {
            throw new IllegalArgumentException();
        }
        BasicOCSPRespBuilder res = null;
        if (respIdType == OcspConfiguration.RESPONDERIDTYPE_NAME) {
            res = new BasicOCSPRespBuilder(new JcaRespID(respondercert.getSubjectX500Principal()));
        } else {
            res = new JcaBasicOCSPRespBuilder(respondercert.getPublicKey(), SHA1DigestCalculator.buildSha1Instance());
        }
        if (req.hasExtensions()) {
            Extension ext = req.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_response);
            if (null != ext) {
                // log.debug("Found extension AcceptableResponses");
                ASN1OctetString oct = ext.getExtnValue();
                try {
                    ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(oct.getOctets()));
                    try {
                        ASN1Sequence seq = ASN1Sequence.getInstance(asn1InputStream.readObject());
                        @SuppressWarnings("unchecked")
                        Enumeration<ASN1ObjectIdentifier> en = seq.getObjects();
                        boolean supportsResponseType = false;
                        while (en.hasMoreElements()) {
                            ASN1ObjectIdentifier oid = en.nextElement();
                            if (oid.equals(OCSPObjectIdentifiers.id_pkix_ocsp_basic)) {
                                // This is the response type we support, so we are happy! Break the loop.
                                supportsResponseType = true;
                                log.debug("Response type supported: " + oid.getId());
                                break;
                            }
                        }
                        if (!supportsResponseType) {
                            throw new NotSupportedException("Required response type not supported, this responder only supports id-pkix-ocsp-basic.");
                        }
                    } finally {
                        asn1InputStream.close();
                    }
                } catch (IOException e) {
                }
            }
        }
        return res;
    }

    /**
     * When a timer expires, this method will update
     * 
     * According to JSR 220 FR (18.2.2), this method may not throw any exceptions.
     * 
     * @param timer The timer whose expiration caused this notification.
     * 
     */
    @Timeout
    /* Glassfish 2.1.1:
     * "Timeout method ....timeoutHandler(javax.ejb.Timer)must have TX attribute of TX_REQUIRES_NEW or TX_REQUIRED or TX_NOT_SUPPORTED"
     * JBoss 5.1.0.GA: We cannot mix timer updates with our EJBCA DataSource transactions. 
     */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void timeoutHandler(Timer timer) {
        if (log.isTraceEnabled()) {
            log.trace(">timeoutHandler: " + timer.getInfo().toString());
        }
        // reloadTokenAndChainCache cancels old timers and adds a new timer
        reloadOcspSigningCache();
        if (log.isTraceEnabled()) {
            log.trace("<timeoutHandler");
        }
    }

    /**
     * This method cancels all timers associated with this bean.
     */
    // We don't want the appserver to persist/update the timer in the same transaction if they are stored in different non XA DataSources. This method
    // should not be run from within a transaction.
    private void cancelTimers() {
        if (log.isTraceEnabled()) {
            log.trace(">cancelTimers");
        }
        Collection<Timer> timers = timerService.getTimers();
        for (Timer timer : timers) {
            timer.cancel();
        }
        if (log.isTraceEnabled()) {
            log.trace("<cancelTimers, timers canceled: " + timers.size());
        }
    }

    /**
     * Adds a timer to the bean
     * 
     * @param id the id of the timer
     */
    // We don't want the appserver to persist/update the timer in the same transaction if they are stored in different non XA DataSources. This method
    // should not be run from within a transaction.
    private Timer addTimer(long interval, Integer id) {
        if (log.isTraceEnabled()) {
            log.trace(">addTimer: " + id + ", interval: " + interval);
        }
        Timer ret = null;
        if (interval > 0) {
            ret = timerService.createTimer(interval, id);
            if (log.isTraceEnabled()) {
                log.trace("<addTimer: " + id + ", interval: " + interval + ", " + ret.getNextTimeout().toString());
            }
        }
        return ret;
    }

    @Override
    public OcspResponseInformation getOcspResponse(final byte[] request, final X509Certificate[] requestCertificates, String remoteAddress,
            String remoteHost, StringBuffer requestUrl, final AuditLogger auditLogger, final TransactionLogger transactionLogger)
            throws MalformedRequestException, IOException, OCSPException {
        //Check parameters
        if (auditLogger == null) {
            throw new InvalidParameterException("Illegal to pass a null audit logger to OcspResponseSession.getOcspResponse");
        }
        if (transactionLogger == null) {
            throw new InvalidParameterException("Illegal to pass a null transaction logger to OcspResponseSession.getOcspResponse");
        }
        // Validate byte array.
        if (request.length > MAX_REQUEST_SIZE) {
            final String msg = intres.getLocalizedMessage("request.toolarge", MAX_REQUEST_SIZE, request.length);
            throw new MalformedRequestException(msg);
        }
        byte[] respBytes = null;
        final Date startTime = new Date();
        OCSPResp ocspResponse = null;
        // Start logging process time after we have received the request
        transactionLogger.paramPut(PatternLogger.PROCESS_TIME, PatternLogger.PROCESS_TIME);
        auditLogger.paramPut(PatternLogger.PROCESS_TIME, PatternLogger.PROCESS_TIME);
        auditLogger.paramPut(AuditLogger.OCSPREQUEST, new String(Hex.encode(request)));
        OCSPReq req;
        long maxAge = OcspConfiguration.getMaxAge(CertificateProfileConstants.CERTPROFILE_NO_PROFILE);
        OCSPRespBuilder responseGenerator = new OCSPRespBuilder();
        try {
            req = translateRequestFromByteArray(request, remoteAddress, transactionLogger);
            // Get the certificate status requests that are inside this OCSP req
            Req[] ocspRequests = req.getRequestList();
            if (ocspRequests.length <= 0) {
                String infoMsg = intres.getLocalizedMessage("ocsp.errornoreqentities");
                log.info(infoMsg);
                throw new MalformedRequestException(infoMsg);
            }
            final int maxRequests = 100;
            if (ocspRequests.length > maxRequests) {
                String infoMsg = intres.getLocalizedMessage("ocsp.errortoomanyreqentities", maxRequests);
                log.info(infoMsg);
                throw new MalformedRequestException(infoMsg);
            }
            if (log.isDebugEnabled()) {
                log.debug("The OCSP request contains " + ocspRequests.length + " simpleRequests.");
            }
            transactionLogger.paramPut(TransactionLogger.NUM_CERT_ID, ocspRequests.length);
            transactionLogger.paramPut(TransactionLogger.STATUS, OCSPRespBuilder.SUCCESSFUL);
            auditLogger.paramPut(AuditLogger.STATUS, OCSPRespBuilder.SUCCESSFUL);
            OcspSigningCacheEntry ocspSigningCacheEntry = null;
            long nextUpdate = OcspConfiguration.getUntilNextUpdate(CertificateProfileConstants.CERTPROFILE_NO_PROFILE);
            // Add standard response extensions
            Map<ASN1ObjectIdentifier, Extension> responseExtensions = getStandardResponseExtensions(req);
            // Look for extension OIDs
            final Collection<String> extensionOids = OcspConfiguration.getExtensionOids();
            // Look over the status requests
            List<OCSPResponseItem> responseList = new ArrayList<OCSPResponseItem>();
            boolean addExtendedRevokedExtension = false;
            Date producedAt = null;
            for (Req ocspRequest : ocspRequests) {
                CertificateID certId = ocspRequest.getCertID();
                transactionLogger.paramPut(TransactionLogger.SERIAL_NOHEX, certId.getSerialNumber().toByteArray());
                transactionLogger.paramPut(TransactionLogger.DIGEST_ALGOR, certId.getHashAlgOID().toString());
                transactionLogger.paramPut(TransactionLogger.ISSUER_NAME_HASH, certId.getIssuerNameHash());
                transactionLogger.paramPut(TransactionLogger.ISSUER_KEY, certId.getIssuerKeyHash());
                auditLogger.paramPut(AuditLogger.ISSUER_KEY, certId.getIssuerKeyHash());
                auditLogger.paramPut(AuditLogger.SERIAL_NOHEX, certId.getSerialNumber().toByteArray());
                auditLogger.paramPut(AuditLogger.ISSUER_NAME_HASH, certId.getIssuerNameHash());
                byte[] hashbytes = certId.getIssuerNameHash();
                String hash = null;
                if (hashbytes != null) {
                    hash = new String(Hex.encode(hashbytes));
                }
                String infoMsg = intres.getLocalizedMessage("ocsp.inforeceivedrequest", certId.getSerialNumber().toString(16), hash, remoteAddress);
                log.info(infoMsg);
                // Locate the CA which gave out the certificate
                ocspSigningCacheEntry = OcspSigningCache.INSTANCE.getEntry(certId);
                if(ocspSigningCacheEntry == null) {
                  //Could it be that we haven't updated the OCSP Signing Cache?
                    ocspSigningCacheEntry = findAndAddMissingCacheEntry(certId);
                }         
                if (ocspSigningCacheEntry != null) {
                    // This will be the issuer DN of the signing certificate, whether an OCSP responder or an internal CA  
                    String issuerNameDn = X500Name.getInstance(
                            ocspSigningCacheEntry.getFullCertificateChain().get(0).getIssuerX500Principal().getEncoded()).toString();
                    transactionLogger.paramPut(TransactionLogger.ISSUER_NAME_DN, issuerNameDn);
                } else { 
                    /*
                     * if the certId was issued by an unknown CA 
                     * 
                     * The algorithm here: 
                     * We will sign the response with the CA that issued the last certificate(certId) in the request. If the issuing CA is not available on 
                     * this server, we sign the response with the default responderId (from params in web.xml). We have to look up the ca-certificate for 
                     * each certId in the request though, as we will check for revocation on the ca-cert as well when checking for revocation on the certId.
                     */
                    
                    // We could not find certificate for this request so get certificate for default responder
                    ocspSigningCacheEntry = OcspSigningCache.INSTANCE.getDefaultEntry();
                    if (ocspSigningCacheEntry != null) {
                        String errMsg = intres.getLocalizedMessage("ocsp.errorfindcacertusedefault",
                                new String(Hex.encode(certId.getIssuerNameHash())));
                        log.info(errMsg);
                        // If we can not find the CA, answer UnknowStatus
                        responseList.add(new OCSPResponseItem(certId, new UnknownStatus(), nextUpdate));
                        transactionLogger.paramPut(TransactionLogger.CERT_STATUS, OCSPResponseItem.OCSP_UNKNOWN);
                        transactionLogger.writeln();
                        continue;
                    } else {
                        String errMsg = intres.getLocalizedMessage("ocsp.errorfindcacert", new String(Hex.encode(certId.getIssuerNameHash())),
                                OcspConfiguration.getDefaultResponderId());
                        log.error(errMsg);
                        // If we are responding to multiple requests, the last found ocspSigningCacheEntry will be used in the end
                        // so even if there are not any one now, it might be later when it is time to sign the responses.
                        // Since we only will sign the entire response once if there is at least one valid ocspSigningCacheEntry
                        // we might as well include the unknown requests.
                        responseList.add(new OCSPResponseItem(certId, new UnknownStatus(), nextUpdate));
                        continue;
                    }
                }
                /*
                 * Implement logic according to chapter 2.7 in RFC2560
                 * 
                 * 2.7 CA Key Compromise If an OCSP responder knows that a particular CA's private key has been compromised, it MAY return the revoked
                 * state for all certificates issued by that CA.
                 */
                final org.bouncycastle.cert.ocsp.CertificateStatus certStatus;
                // Check if the cacert (or the default responderid) is revoked
                X509Certificate caCertificate = ocspSigningCacheEntry.getCaCertificateChain().get(0);
                final CertificateStatus signerIssuerCertStatus = certificateStoreSession.getStatus(CertTools.getIssuerDN(caCertificate),
                        CertTools.getSerialNumber(caCertificate));
                final String caCertificateSubjectDn = CertTools.getSubjectDN(caCertificate);
                if (!signerIssuerCertStatus.equals(CertificateStatus.REVOKED)) {
                    // Check if cert is revoked
                    final CertificateStatus status = certificateStoreSession.getStatus(caCertificateSubjectDn, certId.getSerialNumber());
                    // If we have an OcspKeyBinding configured for this request, we override the default value
                    if (ocspSigningCacheEntry.isUsingSeparateOcspSigningCertificate()) {
                        nextUpdate = ocspSigningCacheEntry.getOcspKeyBinding().getUntilNextUpdate()*1000L;
                    }
                    // If we have an explicit value configured for this certificate profile, we override the the current value with this value
                    if (OcspConfiguration.isUntilNextUpdateConfigured(status.certificateProfileId)) {
                        nextUpdate = OcspConfiguration.getUntilNextUpdate(status.certificateProfileId);
                    }
                    // If we have an OcspKeyBinding configured for this request, we override the default value
                    if (ocspSigningCacheEntry.isUsingSeparateOcspSigningCertificate()) {
                        maxAge = ocspSigningCacheEntry.getOcspKeyBinding().getMaxAge()*1000L;
                    }
                    // If we have an explicit value configured for this certificate profile, we override the the current value with this value
                    if (OcspConfiguration.isMaxAgeConfigured(status.certificateProfileId)) {
                        maxAge = OcspConfiguration.getMaxAge(status.certificateProfileId);
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Set nextUpdate=" + nextUpdate + ", and maxAge=" + maxAge + " for certificateProfileId="
                                + status.certificateProfileId);
                    }
                    final String sStatus;
                    boolean addArchiveCutoff = false;
                    if (status.equals(CertificateStatus.NOT_AVAILABLE)) {
                        // No revocation info available for this cert, handle it
                        if (log.isDebugEnabled()) {
                            log.debug("Unable to find revocation information for certificate with serial '" + certId.getSerialNumber().toString(16)
                                    + "'" + " from issuer '" + caCertificateSubjectDn + "'");
                        }
                        /* 
                         * If we do not treat non existing certificates as good or revoked
                         * OR
                         * we don't actually handle requests for the CA issuing the certificate asked about
                         * then we return unknown 
                         * */
                        if (OcspConfigurationCache.INSTANCE.isNonExistingGood(requestUrl, ocspSigningCacheEntry.getOcspKeyBinding()) &&
                                OcspSigningCache.INSTANCE.getEntry(certId) != null) {
                            sStatus = "good";
                            certStatus = null; // null means "good" in OCSP
                            transactionLogger.paramPut(TransactionLogger.CERT_STATUS, OCSPResponseItem.OCSP_GOOD);
                        } else if (OcspConfigurationCache.INSTANCE.isNonExistingRevoked(requestUrl, ocspSigningCacheEntry.getOcspKeyBinding()) &&
                                OcspSigningCache.INSTANCE.getEntry(certId) != null) {
                            sStatus = "revoked";
                            certStatus = new RevokedStatus(new RevokedInfo(new ASN1GeneralizedTime(new Date(0)),
                                    CRLReason.lookup(CRLReason.certificateHold)));
                            transactionLogger.paramPut(TransactionLogger.CERT_STATUS, OCSPResponseItem.OCSP_REVOKED); 
                            
                            addExtendedRevokedExtension = true;
                            
                        } else {
                            sStatus = "unknown";
                            certStatus = new UnknownStatus();
                            transactionLogger.paramPut(TransactionLogger.CERT_STATUS, OCSPResponseItem.OCSP_UNKNOWN); 
                        }
                    } else if (status.equals(CertificateStatus.REVOKED)) {
                        // Revocation info available for this cert, handle it
                        sStatus = "revoked";
                        certStatus = new RevokedStatus(new RevokedInfo(new ASN1GeneralizedTime(status.revocationDate),
                                CRLReason.lookup(status.revocationReason)));
                        transactionLogger.paramPut(TransactionLogger.CERT_STATUS, OCSPResponseItem.OCSP_REVOKED);
                    } else {
                        sStatus = "good";
                        certStatus = null;
                        transactionLogger.paramPut(TransactionLogger.CERT_STATUS, OCSPResponseItem.OCSP_GOOD);
                        addArchiveCutoff = checkAddArchiveCuttoff(caCertificateSubjectDn, certId);
                    }
                    infoMsg = intres.getLocalizedMessage("ocsp.infoaddedstatusinfo", sStatus, certId.getSerialNumber().toString(16), caCertificateSubjectDn);
                    log.info(infoMsg);
                    OCSPResponseItem respItem = new OCSPResponseItem(certId, certStatus, nextUpdate);
                    if(addArchiveCutoff) {
                        addArchiveCutoff(respItem);
                        producedAt = new Date();
                    }
                    responseList.add(respItem);
                    transactionLogger.writeln();
                } else {
                    certStatus = new RevokedStatus(new RevokedInfo(new ASN1GeneralizedTime(signerIssuerCertStatus.revocationDate),
                            CRLReason.lookup(signerIssuerCertStatus.revocationReason)));
                    infoMsg = intres.getLocalizedMessage("ocsp.infoaddedstatusinfo", "revoked", certId.getSerialNumber().toString(16), caCertificateSubjectDn);
                    log.info(infoMsg);
                    responseList.add(new OCSPResponseItem(certId, certStatus, nextUpdate));
                    transactionLogger.paramPut(TransactionLogger.CERT_STATUS, OCSPResponseItem.OCSP_REVOKED); 
                    transactionLogger.writeln();
                }
                for (String oidstr : extensionOids) {
                    boolean useAlways = false;
                    if (oidstr.startsWith("*")) {
                        oidstr = oidstr.substring(1, oidstr.length());
                        useAlways = true;
                    }
                    ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier(oidstr);
                    Extension ext = null;
                    if (req.hasExtensions()) {
                        ext = req.getExtension(oid);
                    }
                    //If found, or if it should be used anyway
                    if (null != ext || useAlways) {
                        // We found an extension, call the extension class
                        if (log.isDebugEnabled()) {
                            log.debug("Found OCSP extension oid: " + oidstr);
                        }
                        OCSPExtension extObj = OcspExtensionsCache.INSTANCE.getExtensions().get(oidstr);
                        if (extObj != null) {
                            // Find the certificate from the certId
                            X509Certificate cert = null;
                            cert = (X509Certificate) certificateStoreSession.findCertificateByIssuerAndSerno(caCertificateSubjectDn, certId.getSerialNumber());
                            if (cert != null) {
                                // Call the OCSP extension
                                Map<ASN1ObjectIdentifier, Extension> retext = extObj.process(requestCertificates, remoteAddress, remoteHost, cert,
                                        certStatus);
                                if (retext != null) {
                                    // Add the returned X509Extensions to the responseExtension we will add to the basic OCSP response
                                    responseExtensions.putAll(retext);
                                } else {
                                    String errMsg = intres.getLocalizedMessage("ocsp.errorprocessextension", extObj.getClass().getName(),
                                            Integer.valueOf(extObj.getLastErrorCode()));
                                    log.error(errMsg);
                                }
                            }
                        }
                    }
                }
            }
            
            if(addExtendedRevokedExtension) { 
                // id-pkix-ocsp-extended-revoke OBJECT IDENTIFIER ::= {id-pkix-ocsp 9}
                final ASN1ObjectIdentifier extendedRevokedOID = new ASN1ObjectIdentifier(OCSPObjectIdentifiers.pkix_ocsp + ".9");
                responseExtensions.put(extendedRevokedOID, new Extension(extendedRevokedOID, false, DERNull.INSTANCE.getEncoded() ));
            }
            
            if (ocspSigningCacheEntry != null) {
                // Add responseExtensions
                Extensions exts = new Extensions(responseExtensions.values().toArray(new Extension[0]));
                //X509Extensions exts = new X509Extensions(responseExtensions);
                // generate the signed response object
                BasicOCSPResp basicresp = signOcspResponse(req, responseList, exts, ocspSigningCacheEntry, producedAt);
                ocspResponse = responseGenerator.build(OCSPRespBuilder.SUCCESSFUL, basicresp);
                auditLogger.paramPut(AuditLogger.STATUS, OCSPRespBuilder.SUCCESSFUL);
                transactionLogger.paramPut(TransactionLogger.STATUS, OCSPRespBuilder.SUCCESSFUL);
            } else {
                // Only unknown CAs in requests and no default responder's cert
                final String errMsg = intres.getLocalizedMessage("ocsp.errornocacreateresp");
                // This will be logged by the OcspServlet as INFO
                //log.info(errMsg);
                if (log.isTraceEnabled()) {
                    Collection<OcspSigningCacheEntry> entries = OcspSigningCache.INSTANCE.getEntries();
                    if (entries.isEmpty()) {
                        log.trace("OcspSigningCache contains no entries.");
                    } else {
                        log.trace("Dumping contents of OCSP signing cache:");
                        for (OcspSigningCacheEntry ocspSigningCacheEntry2 : entries) {
                            log.trace("SubjectDN: "+ocspSigningCacheEntry2.getOcspSigningCertificate().getSubjectDN().toString());
                            log.trace("- IssuerNameHash: "+new String(Hex.encode(ocspSigningCacheEntry2.getCertificateID().getIssuerNameHash())));
                            log.trace("- IssuerKeyHash: "+new String(Hex.encode(ocspSigningCacheEntry2.getCertificateID().getIssuerKeyHash())));
                        }    	
                    }
                }
                throw new OcspFailureException(errMsg);
            }
        } catch (SignRequestException e) {
            transactionLogger.paramPut(PatternLogger.PROCESS_TIME, PatternLogger.PROCESS_TIME);
            auditLogger.paramPut(PatternLogger.PROCESS_TIME, PatternLogger.PROCESS_TIME);
            String errMsg = intres.getLocalizedMessage("ocsp.errorprocessreq", e.getMessage());
            log.info(errMsg); // No need to log the full exception here
            // RFC 2560: responseBytes are not set on error.
            ocspResponse = responseGenerator.build(OCSPRespBuilder.SIG_REQUIRED, null);
            transactionLogger.paramPut(TransactionLogger.STATUS, OCSPRespBuilder.SIG_REQUIRED);
            transactionLogger.writeln();
            auditLogger.paramPut(AuditLogger.STATUS, OCSPRespBuilder.SIG_REQUIRED);
        } catch (SignRequestSignatureException e) {
            transactionLogger.paramPut(PatternLogger.PROCESS_TIME, PatternLogger.PROCESS_TIME);
            auditLogger.paramPut(PatternLogger.PROCESS_TIME, PatternLogger.PROCESS_TIME);
            String errMsg = intres.getLocalizedMessage("ocsp.errorprocessreq", e.getMessage());
            log.info(errMsg); // No need to log the full exception here
            // RFC 2560: responseBytes are not set on error.
            ocspResponse = responseGenerator.build(OCSPRespBuilder.UNAUTHORIZED, null);
            transactionLogger.paramPut(TransactionLogger.STATUS, OCSPRespBuilder.UNAUTHORIZED);
            transactionLogger.writeln();
            auditLogger.paramPut(AuditLogger.STATUS, OCSPRespBuilder.UNAUTHORIZED);
        } catch (NoSuchAlgorithmException e) {
            ocspResponse = processDefaultError(responseGenerator, transactionLogger, auditLogger, e);
        } catch (CertificateException e) {
            ocspResponse = processDefaultError(responseGenerator, transactionLogger, auditLogger, e);
        } catch (CryptoTokenOfflineException e) {
            ocspResponse = processDefaultError(responseGenerator, transactionLogger, auditLogger, e);
        }
        try {
            respBytes = ocspResponse.getEncoded();
            auditLogger.paramPut(AuditLogger.OCSPRESPONSE, new String(Hex.encode(respBytes)));
            auditLogger.writeln();
            auditLogger.flush();
            transactionLogger.flush();
            if (OcspConfiguration.getLogSafer()) {
                // See if the Errorhandler has found any problems
                if (hasErrorHandlerFailedSince(startTime)) {
                    log.info("ProbableErrorhandler reported error, cannot answer request");
                    // RFC 2560: responseBytes are not set on error.
                    ocspResponse = responseGenerator.build(OCSPRespBuilder.INTERNAL_ERROR, null);

                }
                // See if the Appender has reported any problems
                if (!CanLogCache.INSTANCE.canLog()) {
                    log.info("SaferDailyRollingFileAppender reported error, cannot answer request");
                    // RFC 2560: responseBytes are not set on error.
                    ocspResponse = responseGenerator.build(OCSPRespBuilder.INTERNAL_ERROR, null);
                }
            }
        } catch (IOException e) {
            log.error("", e);
            transactionLogger.flush();
            auditLogger.flush();
        }
        return new OcspResponseInformation(ocspResponse, maxAge);
    }
    
    private boolean checkAddArchiveCuttoff(String caCertificateSubjectDn, CertificateID certId) throws CertificateNotYetValidException {
    
        if(OcspConfiguration.getExpiredArchiveCutoff() == -1) {
            return false;
        }
        
        X509Certificate cert = (X509Certificate) certificateStoreSession.findCertificateByIssuerAndSerno(
                                caCertificateSubjectDn, certId.getSerialNumber());
        try {
            cert.checkValidity();
        } catch(CertificateExpiredException e) {
            log.info("Certificate with serial number '" + certId.getSerialNumber() + "' is not valid. " +
                    "Adding singleExtension id-pkix-ocsp-archive-cutoff");
            return true;
        }
        return false;
    }
    
    private void addArchiveCutoff(OCSPResponseItem respItem) throws IOException {
        long archPeriod = OcspConfiguration.getExpiredArchiveCutoff();
        if(archPeriod == -1) {
            return;
        }
        
        long res = (new Date()).getTime() - archPeriod;
        ExtensionsGenerator gen = new ExtensionsGenerator();
        gen.addExtension(OCSPObjectIdentifiers.id_pkix_ocsp_archive_cutoff, false, new ASN1GeneralizedTime(new Date(res))); 
        Extensions exts = gen.generate();
        respItem.setExtentions(exts);
    }

    /**
     * returns a Map of responseExtensions to be added to the BacisOCSPResponseGenerator with <code>
     * X509Extensions exts = new X509Extensions(table);
     * basicRes.setResponseExtensions(responseExtensions);
     * </code>
     * 
     * @param req OCSPReq
     * @return a HashMap, can be empty but not null
     */
    private Map<ASN1ObjectIdentifier, Extension> getStandardResponseExtensions(OCSPReq req) {
        HashMap<ASN1ObjectIdentifier, Extension> result = new HashMap<ASN1ObjectIdentifier, Extension>();
        if (req.hasExtensions()) {
            // Table of extensions to include in the response
            Extension ext = req.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
            if (null != ext) {
                result.put(OCSPObjectIdentifiers.id_pkix_ocsp_nonce, ext);
            }
        }
        return result;
    }

    /**
     * This method handles cache misses where there exists an active key binding which hasn't been cached.
     * 
     * @param certId the CertificateID for the certificate being requested. 
     * @return the now cached entry, or null if none was found. 
     */
    private OcspSigningCacheEntry findAndAddMissingCacheEntry(CertificateID certId) throws CertificateEncodingException {
        OcspSigningCacheEntry ocspSigningCacheEntry = null;
        for (final int internalKeyBindingId : internalKeyBindingDataSession.getIds(OcspKeyBinding.IMPLEMENTATION_ALIAS)) {
            final OcspKeyBinding ocspKeyBinding = (OcspKeyBinding) internalKeyBindingDataSession.getInternalKeyBinding(internalKeyBindingId);
            if (ocspKeyBinding.getStatus().equals(InternalKeyBindingStatus.ACTIVE)) {
                X509Certificate ocspCertificate = (X509Certificate) certificateStoreSession.findCertificateByFingerprint(ocspKeyBinding
                        .getCertificateId());
                X509Certificate issuingCertificate = certificateStoreSession.findLatestX509CertificateBySubject(CertTools
                        .getIssuerDN(ocspCertificate));
                try {
                    if (certId.matchesIssuer(new JcaX509CertificateHolder(issuingCertificate), new BcDigestCalculatorProvider())) {
                        //We found it! Unless it's not active, or something else was wrong with it. 
                        ocspSigningCacheEntry = makeOcspSigningCacheEntry(ocspCertificate, ocspKeyBinding);
                        //If it was all right, add it to the cache for future use.
                        if (ocspSigningCacheEntry != null) {
                            OcspSigningCache.INSTANCE.addSingleEntry(ocspSigningCacheEntry);
                            break;
                        }
                    }
                } catch (OCSPException e) {
                    throw new IllegalStateException("Could not create BcDigestCalculatorProvider", e);
                }
            }
        }
        return ocspSigningCacheEntry;
    }
    
    private BasicOCSPResp generateBasicOcspResp(OCSPReq ocspRequest, Extensions exts, List<OCSPResponseItem> responses, String sigAlg,
                        X509Certificate signerCert, PrivateKey signerKey, String provider, X509Certificate[] chain, int respIdType, 
                        Date producedAt) throws NotSupportedException, OCSPException, NoSuchProviderException, CryptoTokenOfflineException {
        BasicOCSPResp returnval = null;
        BasicOCSPRespBuilder basicRes = null;
        basicRes = createOcspResponseGenerator(ocspRequest, signerCert, respIdType);
        if (responses != null) {
            for (OCSPResponseItem item : responses) {
                basicRes.addResponse(item.getCertID(), item.getCertStatus(), item.getThisUpdate(), item.getNextUpdate(), item.getExtensions());
            }
        }
        if (exts != null) {
            @SuppressWarnings("rawtypes")
            Enumeration oids = exts.oids();
            if (oids.hasMoreElements()) {
                basicRes.setResponseExtensions(exts);
            }
        }
        /*
         * The below code breaks the EJB standard by creating its own thread pool and creating a single thread (of the HsmResponseThread 
         * type). The reason for this is that the HSM may deadlock when requesting an OCSP response, which we need to guard against. Since 
         * there is no way of performing this action within the EJB3.0 standard, we are consciously creating threads here. 
         * 
         * Note that this does in no way break the spirit of the EJB standard, which is to not interrupt EJB's transaction handling by 
         * competing with its own thread pool, since these operations have no database impact.
         */
        final Future<BasicOCSPResp> task = service.submit(new HsmResponseThread(basicRes, sigAlg, signerKey, chain, provider, producedAt));
        try {
            returnval = task.get(HsmResponseThread.HSM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            task.cancel(true);
            throw new Error("OCSP response retrieval was interrupted while running. This should not happen", e);
        } catch (ExecutionException e) {
            task.cancel(true);
            throw new OcspFailureException("Failure encountered while retrieving OCSP response.", e);
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new CryptoTokenOfflineException("HSM timed out while trying to get OCSP response", e);
        }
        if (log.isDebugEnabled()) {
            log.debug("Signing OCSP response with OCSP signer cert: " + signerCert.getSubjectDN().getName());
        }
        RespID respId = null;
        if (respIdType == OcspConfiguration.RESPONDERIDTYPE_NAME) {
            respId = new JcaRespID(signerCert.getSubjectX500Principal());
        } else {
            respId = new JcaRespID(signerCert.getPublicKey(), SHA1DigestCalculator.buildSha1Instance());
        }
        if (!returnval.getResponderId().equals(respId)) {
            log.error("Response responderId does not match signer certificate responderId!");
            throw new OcspFailureException("Response responderId does not match signer certificate responderId!");
        }
        boolean verify;
        try {
            verify = returnval.isSignatureValid(new JcaContentVerifierProviderBuilder().build(signerCert.getPublicKey()));
        } catch (OperatorCreationException e) {
            // Very fatal error
            throw new EJBException("Can not create Jca content signer: ", e);
        }
        if (verify) {
            if (log.isDebugEnabled()) {
                log.debug("The OCSP response is verifying.");
            }
        } else {
            log.error("The response is NOT verifying! Attempted to sign using " + CertTools.getSubjectDN(signerCert) + " but signature was not valid.");
            throw new OcspFailureException("Attempted to sign using " + CertTools.getSubjectDN(signerCert) + " but signature was not valid.");
        }
        return returnval;
    }

    /**
     * Method that checks with ProbableErrorHandler if an error has happened since a certain time. Uses reflection to call ProbableErrorHandler
     * because it is dependent on JBoss log4j logging, which is not available on other application servers.
     * 
     * @param startTime
     * @return true if an error has occurred since startTime
     */
    private boolean hasErrorHandlerFailedSince(Date startTime) {
        boolean result = true; // Default true. If something goes wrong we will fail
        result = ProbableErrorHandler.hasFailedSince(startTime);
        if (result) {
            log.error("Audit and/or account logging failed since " + startTime);
        }
        return result;
    }
    
    /**
     * Returns a signing algorithm to use selecting from a list of possible algorithms.
     * 
     * @param sigalgs the list of possible algorithms, ;-separated. Example "SHA1WithRSA;SHA1WithECDSA".
     * @param pk public key of signer, so we can choose between RSA, DSA and ECDSA algorithms
     * @return A single algorithm to use Example: SHA1WithRSA, SHA1WithDSA or SHA1WithECDSA
     */
    private static String getSigningAlgFromAlgSelection(String sigalgs, PublicKey pk) {
        String sigAlg = null;
        String[] algs = StringUtils.split(sigalgs, ';');
        for (int i = 0; i < algs.length; i++) {
            if (AlgorithmTools.isCompatibleSigAlg(pk, algs[i])) {
                sigAlg = algs[i];
                break;
            }
        }
        log.debug("Using signature algorithm for response: " + sigAlg);
        return sigAlg;
    }
    
    private static enum CanLogCache {
        INSTANCE;

        private boolean canLog;

        private CanLogCache() {
            this.canLog = true;
        }

        public boolean canLog() {
            return canLog;
        }

        public void setCanLog(boolean canLog) {
            this.canLog = canLog;
        }
    }

    // TODO: Test this thoroughly! 
    @Override
    @Deprecated //Remove this method once upgrading from 5-6 is dropped
    public void adhocUpgradeFromPre60(char[] activationPassword) {
        AuthenticationToken authenticationToken = new AlwaysAllowLocalAuthenticationToken(new UsernamePrincipal(
                OcspResponseGeneratorSessionBean.class.getSimpleName() + ".adhocUpgradeFromPre60"));
        // Check if there are any OcspKeyBindings already, if so return
        if (!internalKeyBindingDataSession.getIds(OcspKeyBinding.IMPLEMENTATION_ALIAS).isEmpty()) {
            return;
        }
        // If ocsp.activation.doNotStorePasswordsInMemory=true, new Crypto Tokens should not be auto-actived
        final boolean globalDoNotStorePasswordsInMemory = OcspConfiguration.getDoNotStorePasswordsInMemory();
        if (globalDoNotStorePasswordsInMemory && activationPassword == null) {
            log.info("Postponing conversion of ocsp.properties configuration to OcspKeyBindings since password is not yet available.");
            return;
        }
        log.info("No OcspKeyBindings found. Processing ocsp.properties to see if we need to perform conversion.");
        final List<InternalKeyBindingTrustEntry> trustDefaults = getOcspKeyBindingTrustDefaults();
        // Create CryptoTokens and AuthenticationKeyBinding from:
        //  ocsp.rekeying.swKeystorePath = wsKeyStore.jks
        //  ocsp.rekeying.swKeystorePassword = foo123
        //  if "ocsp.rekeying.swKeystorePath" isn't set, search the p11 slot later on for an entry with an SSL certificate and use this
        final String swKeystorePath = ConfigurationHolder.getString("ocsp.rekeying.swKeystorePath");
        final String swKeystorePassword = ConfigurationHolder.getString("ocsp.rekeying.swKeystorePassword");
        if (swKeystorePath != null && (swKeystorePassword != null || activationPassword!=null)) {
            final String password = swKeystorePassword==null ? new String(activationPassword) : swKeystorePassword;
            processSoftKeystore(authenticationToken, new File(swKeystorePath), password, password, globalDoNotStorePasswordsInMemory, trustDefaults);
        }
        if (OcspConfiguration.getP11Password() != null || activationPassword != null) {
            log.info(" Processing PKCS#11..");
            final String p11SharedLibrary = OcspConfiguration.getP11SharedLibrary();
            final String sunP11ConfigurationFile = OcspConfiguration.getSunP11ConfigurationFile();
            try {
                final String p11password = OcspConfiguration.getP11Password() == null ? new String(activationPassword) : OcspConfiguration.getP11Password();
                String cryptoTokenName = null;
                final Properties cryptoTokenProperties = new Properties();
                if (p11SharedLibrary != null && p11SharedLibrary.length()!=0) {
                    log.info(" Processing PKCS#11 with shared library " + p11SharedLibrary);
                    final String p11slot = OcspConfiguration.getP11SlotIndex();       
                    cryptoTokenProperties.put(PKCS11CryptoToken.SHLIB_LABEL_KEY, p11SharedLibrary);
                    cryptoTokenProperties.put(PKCS11CryptoToken.SLOT_LABEL_VALUE, p11slot);
                    // Guess label type in order index, number or label 
                    Pkcs11SlotLabelType type;
                    if(Pkcs11SlotLabelType.SLOT_NUMBER.validate(p11slot)) {
                        type = Pkcs11SlotLabelType.SLOT_NUMBER;
                    } else if(Pkcs11SlotLabelType.SLOT_INDEX.validate(p11slot)) {
                        type = Pkcs11SlotLabelType.SLOT_INDEX;
                    } else {
                        type = Pkcs11SlotLabelType.SLOT_LABEL;
                    }
                    cryptoTokenProperties.put(PKCS11CryptoToken.SLOT_LABEL_TYPE, type.getKey());
                    cryptoTokenName = "PKCS11 slot "+p11slot;
                } else if (sunP11ConfigurationFile != null && sunP11ConfigurationFile.length()!=0) {
                    log.info(" Processing PKCS#11 with Sun property file " + sunP11ConfigurationFile);
                    // The following properties are of interest from this file
                    // We will bravely ignore attributes.. it wouldn't be to hard for the user to change the CryptoToken's attributes file later on
                    // name=SafeNet
                    // library=/opt/PTK/lib/libcryptoki.so
                    // slot=1
                    // slotListIndex=1
                    // attributes(...) = {..} 
                    // ...
                    final Properties p11ConfigurationFileProperties = new Properties();
                    p11ConfigurationFileProperties.load(new FileInputStream(sunP11ConfigurationFile));
                    String p11slot = p11ConfigurationFileProperties.getProperty("slot");
                    cryptoTokenProperties.put(PKCS11CryptoToken.SLOT_LABEL_VALUE, p11slot);
                    // Guess label type in order index, number or label 
                    Pkcs11SlotLabelType type;
                    if(Pkcs11SlotLabelType.SLOT_NUMBER.validate(p11slot)) {
                        type = Pkcs11SlotLabelType.SLOT_NUMBER;
                    } else if(Pkcs11SlotLabelType.SLOT_INDEX.validate(p11slot)) {
                        type = Pkcs11SlotLabelType.SLOT_INDEX;
                    } else {
                        type = Pkcs11SlotLabelType.SLOT_LABEL;
                    }
                    cryptoTokenProperties.put(PKCS11CryptoToken.SLOT_LABEL_TYPE, type.getKey());
                    
                    cryptoTokenProperties.put(PKCS11CryptoToken.SHLIB_LABEL_KEY, p11ConfigurationFileProperties.getProperty("library"));
                    //cryptoTokenProperties.put(PKCS11CryptoToken.ATTRIB_LABEL_KEY, null);
                    log.warn("Any attributes(..) = { ... } will be ignored and system defaults will be used."+
                            " You should reconfigure the CryptoToken later if this is not sufficient.");
                    cryptoTokenName = "PKCS11 slot "+p11ConfigurationFileProperties.getProperty("slot", "i" + p11ConfigurationFileProperties.getProperty("slotListIndex"));
                }
                if (cryptoTokenName != null && cryptoTokenManagementSession.getIdFromName(cryptoTokenName) == null) {
                    if (!globalDoNotStorePasswordsInMemory) {
                        log.info(" Auto-activation will be used.");
                        BaseCryptoToken.setAutoActivatePin(cryptoTokenProperties, new String(p11password), true);
                    } else {
                        log.info(" Auto-activation will not be used.");
                    }
                    final int p11CryptoTokenId = cryptoTokenManagementSession.createCryptoToken(authenticationToken, cryptoTokenName,
                            PKCS11CryptoToken.class.getName(), cryptoTokenProperties, null, p11password.toCharArray());
                    // Use reflection to dig out the certificate objects for each alias so we can create an internal key binding for it
                    final Method m = BaseCryptoToken.class.getDeclaredMethod("getKeyStore");
                    m.setAccessible(true);
                    final KeyStore keyStore = (KeyStore) m.invoke(cryptoTokenManagementSession.getCryptoToken(p11CryptoTokenId));
                    createInternalKeyBindings(authenticationToken, p11CryptoTokenId, keyStore, trustDefaults);
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
        if (OcspConfiguration.getSoftKeyDirectoryName() != null && (OcspConfiguration.getStorePassword() != null || activationPassword != null)) {
            final String softStorePassword = OcspConfiguration.getStorePassword() == null ? new String(activationPassword) : OcspConfiguration.getStorePassword();
            final String softKeyPassword = OcspConfiguration.getKeyPassword() == null ? new String(activationPassword) : OcspConfiguration.getKeyPassword();
            final String dirName = OcspConfiguration.getSoftKeyDirectoryName();
            if (dirName != null) {
                final File directory = new File(dirName);
                if (directory.isDirectory()) {
                    log.info(" Processing Soft KeyStores..");
                    for (final File file : directory.listFiles()) {
                        processSoftKeystore(authenticationToken, file, softStorePassword, softKeyPassword, globalDoNotStorePasswordsInMemory, trustDefaults);
                    }
                }
            }
        }
    }
    
    @Deprecated //Remove this method as soon as upgrading from 5.0->6.x is dropped
    private void processSoftKeystore(AuthenticationToken authenticationToken, File file, String softStorePassword, String softKeyPassword,
            boolean doNotStorePasswordsInMemory, List<InternalKeyBindingTrustEntry> trustDefaults) {
     KeyStore keyStore;
        final char[] passwordChars = softStorePassword.toCharArray();
        // Load keystore (JKS or PKCS#12)
        try {
            keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(file), passwordChars);
        } catch (Exception e) {
            try {
                keyStore = KeyStore.getInstance("PKCS12", "BC");
                keyStore.load(new FileInputStream(file), passwordChars);
            } catch (Exception e2) {
                try {
                    log.info("Unable to process " + file.getCanonicalPath() + " as a KeyStore.");
                } catch (IOException e3) {
                    log.warn(e3.getMessage());
                }
                return;
            }
        }
        
        // Strip issuer certs, etc. and convert to PKCS#12
        try {
            keyStore = makeKeysOnlyP12(keyStore, passwordChars);
        } catch (Exception e) {
            throw new RuntimeException("failed to convert keystore to P12 during keybindings upgrade", e);
        }
        
        final String name = file.getName();
        if (cryptoTokenManagementSession.getIdFromName(name) != null) {
            return; // already upgraded
        }
        log.info(" Processing Soft KeyStore '" + name + "' of type " + keyStore.getType());
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Save the store using the same password as the keys are protected with (not the store password)
            // so we don't have to replace the protection for each key
            keyStore.store(baos, softKeyPassword.toCharArray());
            final Properties cryptoTokenProperties = new Properties();
            if (!doNotStorePasswordsInMemory) {
                log.info(" Auto-activation will be used.");
                BaseCryptoToken.setAutoActivatePin(cryptoTokenProperties, new String(softKeyPassword), true);
            } else {
                log.info(" Auto-activation will not be used.");
            }
            final int softCryptoTokenId = cryptoTokenManagementSession.createCryptoToken(authenticationToken, name,
                    SoftCryptoToken.class.getName(), cryptoTokenProperties, baos.toByteArray(), softKeyPassword.toCharArray());
            createInternalKeyBindings(authenticationToken, softCryptoTokenId, keyStore, trustDefaults);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }
    
    /** Creates a PKCS#12 KeyStore with keys only from an JKS file (no issuer certs or trusted certs) */
    @Deprecated  //Remove this method as soon as upgrading from 5->6 is dropped
    private KeyStore makeKeysOnlyP12(KeyStore keyStore, char[] password) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException, NoSuchProviderException, CertificateException, IOException {
        final KeyStore p12 = KeyStore.getInstance("PKCS12", "BC");
        final KeyStore.ProtectionParameter protParam =
            (password != null ? new KeyStore.PasswordProtection(password) : null);
        p12.load(null, password); // initialize
        
        final Enumeration<String> en = keyStore.aliases();
        while (en.hasMoreElements()) {
            final String alias = en.nextElement();
            if (!keyStore.isKeyEntry(alias)) continue;
            try {
                KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(alias, protParam);
                Certificate[] chain = new Certificate[] { entry.getCertificate() };
                p12.setKeyEntry(alias, entry.getPrivateKey(), password, chain);
            } catch (UnsupportedOperationException uoe) {
                KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(alias, null);
                Certificate[] chain = new Certificate[] { entry.getCertificate() };
                p12.setKeyEntry(alias, entry.getPrivateKey(), null, chain);
            }
        }
        return p12;
    }
    
    /** Create InternalKeyBindings for Ocsp signing and SSL client authentication certs during ad-hoc upgrades. */
    @Deprecated //Remove this method as soon as upgrading from 5->6 is dropped
    private void createInternalKeyBindings(AuthenticationToken authenticationToken, int cryptoTokenId, KeyStore keyStore, List<InternalKeyBindingTrustEntry> trustDefaults) throws KeyStoreException, CryptoTokenOfflineException, InternalKeyBindingNameInUseException, AuthorizationDeniedException, CertificateEncodingException, CertificateImportException, InvalidAlgorithmException {
        final Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            final String keyPairAlias = aliases.nextElement();
            log.info("Found alias " + keyPairAlias + ", trying to figure out if this is something we should convert into a new KeyBinding...");
            final Certificate[] chain = keyStore.getCertificateChain(keyPairAlias);
            if (chain == null || chain.length==0) {
                log.info("Alias " + keyPairAlias + " does not contain any certificate and will be ignored.");
                continue;   // Ignore entry
            }
            // Extract the default signature algorithm
            final String signatureAlgorithm = getSigningAlgFromAlgSelection(OcspConfiguration.getSignatureAlgorithm(), chain[0].getPublicKey());
            if (OcspKeyBinding.isOcspSigningCertificate(chain[0])) {
                // Create the actual OcspKeyBinding
                log.info("Alias " + keyPairAlias + " contains an OCSP certificate and will be converted to an OcspKeyBinding.");
                int internalKeyBindingId = internalKeyBindingMgmtSession.createInternalKeyBinding(authenticationToken, OcspKeyBinding.IMPLEMENTATION_ALIAS,
                        "OcspKeyBinding for " + keyPairAlias, InternalKeyBindingStatus.DISABLED, null, cryptoTokenId, keyPairAlias, signatureAlgorithm,
                        getOcspKeyBindingDefaultProperties());
                InternalKeyBinding internalKeyBinding = internalKeyBindingMgmtSession.getInternalKeyBinding(authenticationToken, internalKeyBindingId);
                internalKeyBinding.setTrustedCertificateReferences(trustDefaults);
                internalKeyBindingMgmtSession.persistInternalKeyBinding(authenticationToken, internalKeyBinding);
                internalKeyBindingMgmtSession.importCertificateForInternalKeyBinding(authenticationToken, internalKeyBindingId, chain[0].getEncoded());
                internalKeyBindingMgmtSession.setStatus(authenticationToken, internalKeyBindingId, InternalKeyBindingStatus.ACTIVE);
            } else if (AuthenticationKeyBinding.isClientSSLCertificate(chain[0])) {
                log.info("Alias " + keyPairAlias + " contains an SSL client certificate and will be converted to an AuthenticationKeyBinding.");
                // We are looking for an SSL cert, use this to create an AuthenticationKeyBinding
                int internalKeyBindingId = internalKeyBindingMgmtSession.createInternalKeyBinding(authenticationToken, AuthenticationKeyBinding.IMPLEMENTATION_ALIAS,
                        "AuthenticationKeyBinding for " + keyPairAlias, InternalKeyBindingStatus.DISABLED, null, cryptoTokenId, keyPairAlias,
                        signatureAlgorithm, null);
                internalKeyBindingMgmtSession.importCertificateForInternalKeyBinding(authenticationToken, internalKeyBindingId, chain[0].getEncoded());
                internalKeyBindingMgmtSession.setStatus(authenticationToken, internalKeyBindingId, InternalKeyBindingStatus.ACTIVE);
            } else {
                log.info("Alias " + keyPairAlias + " contains certificate of unknown type and will be ignored.");
            }
        }
    }

    /** @return a list of trusted signers or CAs */
    @Deprecated //This method is only used for upgrading to version 6
    private List<InternalKeyBindingTrustEntry> getOcspKeyBindingTrustDefaults() {
        // Import certificates used to verify OCSP request signatures and add these to each OcspKeyBinding's trust-list
        //  ocsp.signtrustdir=signtrustdir
        //  ocsp.signtrustvalidtime should be ignored
        final List<InternalKeyBindingTrustEntry> trustedCertificateReferences = new ArrayList<InternalKeyBindingTrustEntry>();
        if (OcspConfiguration.getEnforceRequestSigning() && OcspConfiguration.getRestrictSignatures()) {
            // Import certificates and configure Issuer+serialnumber in trustlist for each
            final String dirName = OcspConfiguration.getSignTrustDir();
            if (dirName != null) {
                final File directory = new File(dirName);
                if (directory.isDirectory()) {
                    for (final File file : directory.listFiles()) {
                        try {
                            final List<Certificate> chain = CertTools.getCertsFromPEM(new FileInputStream(file));
                            if (!chain.isEmpty()) {
                                final String issuerDn = CertTools.getIssuerDN(chain.get(0));
                                final String subjectDn = CertTools.getSubjectDN(chain.get(0));
                                if (OcspConfiguration.getRestrictSignaturesByMethod()==OcspConfiguration.RESTRICTONSIGNER) {
                                    final int caId = issuerDn.hashCode();
                                    final BigInteger serialNumber = CertTools.getSerialNumber(chain.get(0));
                                    if(!caSession.existsCa(caId)) { 
                                        log.warn("Trusted certificate with serialNumber " + serialNumber.toString(16) +
                                                " is issued by an unknown CA with subject '" + issuerDn +
                                                "'. You should import this CA certificate as en external CA to make it known to the system.");
                                    }
                                    trustedCertificateReferences.add(new InternalKeyBindingTrustEntry(caId, serialNumber));
                                } else {
                                    final int caId = subjectDn.hashCode();
                                    if(!caSession.existsCa(caId)) { 
                                        log.warn("Trusted CA certificate with with subject '" + subjectDn +
                                                "' should be imported as en external CA to make it known to the system.");
                                    }
                                    trustedCertificateReferences.add(new InternalKeyBindingTrustEntry(caId, null));
                                }
                            }
                        } catch (CertificateException e) {
                            log.warn(e.getMessage());
                        } catch (FileNotFoundException e) {
                            log.warn(e.getMessage());
                        } catch (IOException e) {
                            log.warn(e.getMessage());
                        }
                    }
                }
            }
        }
        return trustedCertificateReferences;
    }
    
    /** @return OcspKeyBinding properties set to the current file-based configuration (per cert profile config is ignored here) */
    private Map<String, Serializable> getOcspKeyBindingDefaultProperties() {
        // Use global config as defaults for each new OcspKeyBinding
        final Map<String, Serializable> dataMap = new HashMap<String, Serializable>();
        dataMap.put(OcspKeyBinding.PROPERTY_INCLUDE_CERT_CHAIN, Boolean.valueOf(OcspConfiguration.getIncludeCertChain()));
        if (OcspConfiguration.getResponderIdType()==OcspConfiguration.RESPONDERIDTYPE_NAME) {
            dataMap.put(OcspKeyBinding.PROPERTY_RESPONDER_ID_TYPE, ResponderIdType.NAME.name());
        } else {
            dataMap.put(OcspKeyBinding.PROPERTY_RESPONDER_ID_TYPE, ResponderIdType.KEYHASH.name());
        }
        dataMap.put(OcspKeyBinding.PROPERTY_MAX_AGE, (int)(OcspConfiguration.getMaxAge(CertificateProfileConstants.CERTPROFILE_NO_PROFILE)/1000L));
        dataMap.put(OcspKeyBinding.PROPERTY_NON_EXISTING_GOOD, Boolean.valueOf(OcspConfiguration.getNonExistingIsGood()));
        dataMap.put(OcspKeyBinding.PROPERTY_NON_EXISTING_REVOKED, Boolean.valueOf(OcspConfiguration.getNonExistingIsRevoked()));
        dataMap.put(OcspKeyBinding.PROPERTY_UNTIL_NEXT_UPDATE, (long)(OcspConfiguration.getUntilNextUpdate(CertificateProfileConstants.CERTPROFILE_NO_PROFILE)/1000L));
        dataMap.put(OcspKeyBinding.PROPERTY_REQUIRE_TRUSTED_SIGNATURE, Boolean.valueOf(OcspConfiguration.getEnforceRequestSigning()));
        return dataMap;
    }
    
    @Override
    public String healthCheck() {
        final StringBuilder sb = new StringBuilder();
        // Check that there are no ACTIVE OcspKeyBindings that are not in the cache before checking usability..
        for (InternalKeyBindingInfo internalKeyBindingInfo : internalKeyBindingMgmtSession
                .getAllInternalKeyBindingInfos(OcspKeyBinding.IMPLEMENTATION_ALIAS)) {
            if (internalKeyBindingInfo.getStatus().equals(InternalKeyBindingStatus.ACTIVE)) {
                Certificate ocspCertificate = certificateStoreSession.findCertificateByFingerprint(internalKeyBindingInfo.getCertificateId());
                X509Certificate issuingCertificate = certificateStoreSession.findLatestX509CertificateBySubject(CertTools
                        .getIssuerDN(ocspCertificate));
                CertificateID certId = OcspSigningCache.getCertificateIDFromCertificate(issuingCertificate);
                OcspSigningCacheEntry ocspSigningCacheEntry = OcspSigningCache.INSTANCE.getEntry(certId);
                if(ocspSigningCacheEntry == null) {
                    //Could be a cache issue?
                    try {
                        ocspSigningCacheEntry = findAndAddMissingCacheEntry(certId);
                    } catch (CertificateEncodingException e) {
                       throw new IllegalStateException("Could not process certificate", e);
                    }
                }
                
                if (ocspSigningCacheEntry == null) {
                    final String errMsg = intres.getLocalizedMessage("ocsp.signingkeynotincache", internalKeyBindingInfo.getName());
                    sb.append('\n').append(errMsg);
                    log.error(errMsg);
                }
            }
        }
        if(!sb.toString().equals("")) {
            return sb.toString();
        }
        try {
            final Collection<OcspSigningCacheEntry> ocspSigningCacheEntries = OcspSigningCache.INSTANCE.getEntries();
            if (ocspSigningCacheEntries.isEmpty()) {
                // Only report this in the server log. It is not an erroneous state to have no ACTIVE OcspKeyBindings.
                if (log.isDebugEnabled()) {
                    log.debug(intres.getLocalizedMessage("ocsp.errornosignkeys"));
                }
            } else {
                for (OcspSigningCacheEntry ocspSigningCacheEntry : ocspSigningCacheEntries) {
                    // Only verify non-CA responders
                    final X509Certificate ocspSigningCertificate = ocspSigningCacheEntry.getOcspSigningCertificate();
                    if (ocspSigningCertificate == null) {
                        continue;
                    }
                    final String subjectDn = CertTools.getSubjectDN(ocspSigningCacheEntry.getCaCertificateChain().get(0));
                    final String serialNumber = CertTools.getSerialNumber(ocspSigningCacheEntry.getOcspSigningCertificate()).toString(16);
                    final String errMsg = intres.getLocalizedMessage("ocsp.errorocspkeynotusable", subjectDn, serialNumber);
                    final PrivateKey privateKey = ocspSigningCacheEntry.getPrivateKey();
                    if (privateKey == null) {
                        sb.append('\n').append(errMsg);
                        log.error("No key available. " + errMsg);
                        continue;
                    }
                    if (OcspConfiguration.getHealthCheckCertificateValidity() && !CertTools.isCertificateValid(ocspSigningCertificate) ) {
                        sb.append('\n').append(errMsg);
                        continue;
                    }
                    if (OcspConfiguration.getHealthCheckSignTest()) {
                        try {
                            final String providerName = ocspSigningCacheEntry.getSignatureProviderName();
                            KeyTools.testKey(privateKey, ocspSigningCertificate.getPublicKey(), providerName);
                        } catch (InvalidKeyException e) {
                            // thrown by testKey
                            sb.append('\n').append(errMsg);
                            log.error("Key not working. SubjectDN '"+subjectDn+"'. Error comment '"+errMsg+"'. Message '"+e.getMessage());
                            continue;                   
                        }
                    }
                    if (log.isDebugEnabled()) {
                        final String name = ocspSigningCacheEntry.getOcspKeyBinding().getName();
                        log.debug("Test of \""+name+"\" OK!");                          
                    }
                }
            }
        } catch (Exception e) {
            final String errMsg = intres.getLocalizedMessage("ocsp.errorloadsigningcerts");
            log.error(errMsg, e);
            sb.append(errMsg).append(": ").append(errMsg);
        }
        return sb.toString();
    }

}

class CardKeyHolder {
    private static final InternalResources intres = InternalResources.getInstance();
    private static CardKeyHolder instance = null;
    private CardKeys cardKeys = null;

    private CardKeyHolder() {
        Logger log = Logger.getLogger(CardKeyHolder.class);
        String hardTokenClassName = OcspConfiguration.getHardTokenClassName();
        try {
            this.cardKeys = (CardKeys) OcspResponseGeneratorSessionBean.class.getClassLoader().loadClass(hardTokenClassName).newInstance();
            this.cardKeys.autenticate(OcspConfiguration.getCardPassword());
        } catch (ClassNotFoundException e) {
            log.debug(intres.getLocalizedMessage("ocsp.classnotfound", hardTokenClassName));
        } catch (Exception e) {
            log.info("Could not create CardKeyHolder", e);
        }
    }

    public static synchronized CardKeyHolder getInstance() {
        if (instance == null) {
            instance = new CardKeyHolder();
        }
        return instance;
    }

    public CardKeys getCardKeys() {
        return cardKeys;
    }

}
