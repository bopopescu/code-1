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
package org.cesecore.certificates.ca;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CRLException;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuingDistributionPoint;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509DefaultEntryConverter;
import org.bouncycastle.asn1.x509.X509NameEntryConverter;
import org.bouncycastle.cert.CertException;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSSignedGenerator;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.operator.BufferingContentSigner;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.encoders.Hex;
import org.cesecore.certificates.ca.catoken.CAToken;
import org.cesecore.certificates.ca.catoken.CATokenConstants;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAService;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceInfo;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceTypes;
import org.cesecore.certificates.ca.internal.CertificateValidity;
import org.cesecore.certificates.ca.internal.SernoGeneratorRandom;
import org.cesecore.certificates.certificate.CertificateConstants;
import org.cesecore.certificates.certificate.CertificateCreateException;
import org.cesecore.certificates.certificate.certextensions.CertificateExtension;
import org.cesecore.certificates.certificate.certextensions.CertificateExtensionFactory;
import org.cesecore.certificates.certificate.request.RequestMessage;
import org.cesecore.certificates.certificateprofile.CertificatePolicy;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.crl.RevokedCertInfo;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.EndEntityType;
import org.cesecore.certificates.endentity.EndEntityTypes;
import org.cesecore.certificates.endentity.ExtendedInformation;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.certificates.util.dn.PrintableStringEntryConverter;
import org.cesecore.config.CesecoreConfiguration;
import org.cesecore.internal.InternalResources;
import org.cesecore.keys.token.CryptoToken;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.cesecore.keys.token.IllegalCryptoTokenException;
import org.cesecore.keys.token.NullCryptoToken;
import org.cesecore.util.CertTools;
import org.cesecore.util.SimpleTime;
import org.cesecore.util.StringTools;

/**
 * X509CA is a implementation of a CA and holds data specific for Certificate and CRL generation according to the X509 standard.
 * 
 * @version $Id: X509CA.java 18176 2013-11-18 15:11:01Z samuellb $
 */
public class X509CA extends CA implements Serializable {

    private static final long serialVersionUID = -2882572653108530258L;

    private static final Logger log = Logger.getLogger(X509CA.class);

    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();

    /** Version of this class, if this is increased the upgrade() method will be called automatically */
    public static final float LATEST_VERSION = 19;

    /** key ID used for identifier of key used for key recovery encryption */
    private byte[] keyId = new byte[] { 1, 2, 3, 4, 5 };

    // protected fields for properties specific to this type of CA.
    protected static final String POLICIES = "policies";
    protected static final String SUBJECTALTNAME = "subjectaltname";
    protected static final String USEAUTHORITYKEYIDENTIFIER = "useauthoritykeyidentifier";
    protected static final String AUTHORITYKEYIDENTIFIERCRITICAL = "authoritykeyidentifiercritical";
    protected static final String AUTHORITY_INFORMATION_ACCESS = "authorityinformationaccess";
    protected static final String USECRLNUMBER = "usecrlnumber";
    protected static final String CRLNUMBERCRITICAL = "crlnumbercritical";
    protected static final String DEFAULTCRLDISTPOINT = "defaultcrldistpoint";
    protected static final String DEFAULTCRLISSUER = "defaultcrlissuer";
    protected static final String DEFAULTOCSPSERVICELOCATOR = "defaultocspservicelocator";
    protected static final String CADEFINEDFRESHESTCRL = "cadefinedfreshestcrl";
    protected static final String USEUTF8POLICYTEXT = "useutf8policytext";
    protected static final String USEPRINTABLESTRINGSUBJECTDN = "useprintablestringsubjectdn";
    protected static final String USELDAPDNORDER = "useldapdnorder";
    protected static final String USECRLDISTRIBUTIONPOINTONCRL = "usecrldistributionpointoncrl";
    protected static final String CRLDISTRIBUTIONPOINTONCRLCRITICAL = "crldistributionpointoncrlcritical";
    protected static final String CMPRAAUTHSECRET = "cmpraauthsecret";

    // Public Methods
    /** Creates a new instance of CA, this constructor should be used when a new CA is created */
    public X509CA(final X509CAInfo cainfo) {
        super(cainfo);

        data.put(POLICIES, cainfo.getPolicies());
        data.put(SUBJECTALTNAME, cainfo.getSubjectAltName());
        setUseAuthorityKeyIdentifier(cainfo.getUseAuthorityKeyIdentifier());
        setAuthorityKeyIdentifierCritical(cainfo.getAuthorityKeyIdentifierCritical());
        setUseCRLNumber(cainfo.getUseCRLNumber());
        setCRLNumberCritical(cainfo.getCRLNumberCritical());
        setDefaultCRLDistPoint(cainfo.getDefaultCRLDistPoint());
        setDefaultCRLIssuer(cainfo.getDefaultCRLIssuer());
        setCADefinedFreshestCRL(cainfo.getCADefinedFreshestCRL());
        setDefaultOCSPServiceLocator(cainfo.getDefaultOCSPServiceLocator());
        setUseUTF8PolicyText(cainfo.getUseUTF8PolicyText());
        setUsePrintableStringSubjectDN(cainfo.getUsePrintableStringSubjectDN());
        setUseLdapDNOrder(cainfo.getUseLdapDnOrder());
        setUseCrlDistributionPointOnCrl(cainfo.getUseCrlDistributionPointOnCrl());
        setCrlDistributionPointOnCrlCritical(cainfo.getCrlDistributionPointOnCrlCritical());
        setCmpRaAuthSecret(cainfo.getCmpRaAuthSecret());
        setAuthorityInformationAccess(cainfo.getAuthorityInformationAccess());
        data.put(CA.CATYPE, Integer.valueOf(CAInfo.CATYPE_X509));
        data.put(VERSION, new Float(LATEST_VERSION));
    }

    /**
     * Constructor used when retrieving existing X509CA from database.
     * 
     * @throws IllegalCryptoTokenException
     */
    @SuppressWarnings("deprecation")
    public X509CA(final HashMap<Object, Object> data, final int caId, final String subjectDN, final String name, final int status,
            final Date updateTime, final Date expireTime) {
        super(data);
        setExpireTime(expireTime); // Make sure the internal state is synched with the database column. Required for upgrades from EJBCA 3.5.6 or
                                   // EJBCA 3.6.1 and earlier.
        final List<ExtendedCAServiceInfo> externalcaserviceinfos = new ArrayList<ExtendedCAServiceInfo>();
        for (final Integer type : getExternalCAServiceTypes()) {
            //Type was removed in 6.0.0. It is removed from the database in the upgrade method in this class, but it needs to be ignored 
            //for instantiation. 
            if (type != ExtendedCAServiceTypes.TYPE_OCSPEXTENDEDSERVICE) {
                ExtendedCAServiceInfo info = this.getExtendedCAServiceInfo(type.intValue());
                if (info != null) {
                    externalcaserviceinfos.add(info);
                }
            }
        }
        CAInfo info = new X509CAInfo(subjectDN, name, status, updateTime, getSubjectAltName(), getCertificateProfileId(), getValidity(),
                getExpireTime(), getCAType(), getSignedBy(), getCertificateChain(), getCAToken(), getDescription(),
                getRevocationReason(), getRevocationDate(), getPolicies(), getCRLPeriod(), getCRLIssueInterval(), getCRLOverlapTime(),
                getDeltaCRLPeriod(), getCRLPublishers(), getUseAuthorityKeyIdentifier(), getAuthorityKeyIdentifierCritical(), getUseCRLNumber(),
                getCRLNumberCritical(), getDefaultCRLDistPoint(), getDefaultCRLIssuer(), getDefaultOCSPServiceLocator(), getAuthorityInformationAccess(), getCADefinedFreshestCRL(),
                getFinishUser(), externalcaserviceinfos, getUseUTF8PolicyText(), getApprovalSettings(), getNumOfRequiredApprovals(),
                getUsePrintableStringSubjectDN(), getUseLdapDNOrder(), getUseCrlDistributionPointOnCrl(), getCrlDistributionPointOnCrlCritical(),
                getIncludeInHealthCheck(), isDoEnforceUniquePublicKeys(), isDoEnforceUniqueDistinguishedName(),
                isDoEnforceUniqueSubjectDNSerialnumber(), isUseCertReqHistory(), isUseUserStorage(), isUseCertificateStorage(), getCmpRaAuthSecret());
        super.setCAInfo(info);
        setCAId(caId);
    }

    // Public Methods.
    @SuppressWarnings("unchecked")
    public List<CertificatePolicy> getPolicies() {
        return (List<CertificatePolicy>) data.get(POLICIES);
    }

    public void setPolicies(List<CertificatePolicy> policies) {
        data.put(POLICIES, policies);
    }

    public String getSubjectAltName() {
        return (String) data.get(SUBJECTALTNAME);
    }

    public boolean getUseAuthorityKeyIdentifier() {
        return ((Boolean) data.get(USEAUTHORITYKEYIDENTIFIER)).booleanValue();
    }

    public void setUseAuthorityKeyIdentifier(boolean useauthoritykeyidentifier) {
        data.put(USEAUTHORITYKEYIDENTIFIER, Boolean.valueOf(useauthoritykeyidentifier));
    }

    public boolean getAuthorityKeyIdentifierCritical() {
        return ((Boolean) data.get(AUTHORITYKEYIDENTIFIERCRITICAL)).booleanValue();
    }

    public void setAuthorityKeyIdentifierCritical(boolean authoritykeyidentifiercritical) {
        data.put(AUTHORITYKEYIDENTIFIERCRITICAL, Boolean.valueOf(authoritykeyidentifiercritical));
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getAuthorityInformationAccess() {
        return (List<String>) data.get(AUTHORITY_INFORMATION_ACCESS);
    }

    public void setAuthorityInformationAccess(Collection<String> authorityInformationAccess) {
        data.put(AUTHORITY_INFORMATION_ACCESS, authorityInformationAccess);
    }
    
    public boolean getUseCRLNumber() {
        return ((Boolean) data.get(USECRLNUMBER)).booleanValue();
    }

    public void setUseCRLNumber(boolean usecrlnumber) {
        data.put(USECRLNUMBER, Boolean.valueOf(usecrlnumber));
    }

    public boolean getCRLNumberCritical() {
        return ((Boolean) data.get(CRLNUMBERCRITICAL)).booleanValue();
    }

    public void setCRLNumberCritical(boolean crlnumbercritical) {
        data.put(CRLNUMBERCRITICAL, Boolean.valueOf(crlnumbercritical));
    }

    public String getDefaultCRLDistPoint() {
        return (String) data.get(DEFAULTCRLDISTPOINT);
    }

    public void setDefaultCRLDistPoint(String defaultcrldistpoint) {
        if (defaultcrldistpoint == null) {
            data.put(DEFAULTCRLDISTPOINT, "");
        } else {
            data.put(DEFAULTCRLDISTPOINT, defaultcrldistpoint);
        }
    }

    public String getDefaultCRLIssuer() {
        return (String) data.get(DEFAULTCRLISSUER);
    }

    public void setDefaultCRLIssuer(String defaultcrlissuer) {
        if (defaultcrlissuer == null) {
            data.put(DEFAULTCRLISSUER, "");
        } else {
            data.put(DEFAULTCRLISSUER, defaultcrlissuer);
        }
    }

    public String getCADefinedFreshestCRL() {
        return (String) data.get(CADEFINEDFRESHESTCRL);
    }

    public void setCADefinedFreshestCRL(String cadefinedfreshestcrl) {
        if (cadefinedfreshestcrl == null) {
            data.put(CADEFINEDFRESHESTCRL, "");
        } else {
            data.put(CADEFINEDFRESHESTCRL, cadefinedfreshestcrl);
        }
    }

    public String getDefaultOCSPServiceLocator() {
        return (String) data.get(DEFAULTOCSPSERVICELOCATOR);
    }

    public void setDefaultOCSPServiceLocator(String defaultocsplocator) {
        if (defaultocsplocator == null) {
            data.put(DEFAULTOCSPSERVICELOCATOR, "");
        } else {
            data.put(DEFAULTOCSPSERVICELOCATOR, defaultocsplocator);
        }
    }

    public boolean getUseUTF8PolicyText() {
        return ((Boolean) data.get(USEUTF8POLICYTEXT)).booleanValue();
    }

    public void setUseUTF8PolicyText(boolean useutf8) {
        data.put(USEUTF8POLICYTEXT, Boolean.valueOf(useutf8));
    }

    public boolean getUsePrintableStringSubjectDN() {
        return ((Boolean) data.get(USEPRINTABLESTRINGSUBJECTDN)).booleanValue();
    }

    public void setUsePrintableStringSubjectDN(boolean useprintablestring) {
        data.put(USEPRINTABLESTRINGSUBJECTDN, Boolean.valueOf(useprintablestring));
    }

    public boolean getUseLdapDNOrder() {
        return ((Boolean) data.get(USELDAPDNORDER)).booleanValue();
    }

    public void setUseLdapDNOrder(boolean useldapdnorder) {
        data.put(USELDAPDNORDER, Boolean.valueOf(useldapdnorder));
    }

    public boolean getUseCrlDistributionPointOnCrl() {
        return ((Boolean) data.get(USECRLDISTRIBUTIONPOINTONCRL)).booleanValue();
    }

    public void setUseCrlDistributionPointOnCrl(boolean useCrlDistributionPointOnCrl) {
        data.put(USECRLDISTRIBUTIONPOINTONCRL, Boolean.valueOf(useCrlDistributionPointOnCrl));
    }

    public boolean getCrlDistributionPointOnCrlCritical() {
        return ((Boolean) data.get(CRLDISTRIBUTIONPOINTONCRLCRITICAL)).booleanValue();
    }

    public void setCrlDistributionPointOnCrlCritical(boolean crlDistributionPointOnCrlCritical) {
        data.put(CRLDISTRIBUTIONPOINTONCRLCRITICAL, Boolean.valueOf(crlDistributionPointOnCrlCritical));
    }

    public String getCmpRaAuthSecret() {
        Object o = data.get(CMPRAAUTHSECRET);
        if (o == null) {
            // Default to empty value if it is not set. An empty value will be denied by CRMFMessageHandler
            return "";
        }
        return (String) o;
    }

    public void setCmpRaAuthSecret(String cmpRaAuthSecret) {
        data.put(CMPRAAUTHSECRET, cmpRaAuthSecret);
    }

    public void updateCA(CryptoToken cryptoToken, CAInfo cainfo) throws InvalidAlgorithmException {
        super.updateCA(cryptoToken, cainfo);
        X509CAInfo info = (X509CAInfo) cainfo;
        setPolicies(info.getPolicies());
        setAuthorityInformationAccess(info.getAuthorityInformationAccess());
        setUseAuthorityKeyIdentifier(info.getUseAuthorityKeyIdentifier());
        setAuthorityKeyIdentifierCritical(info.getAuthorityKeyIdentifierCritical());
        setUseCRLNumber(info.getUseCRLNumber());
        setCRLNumberCritical(info.getCRLNumberCritical());
        setDefaultCRLDistPoint(info.getDefaultCRLDistPoint());
        setDefaultCRLIssuer(info.getDefaultCRLIssuer());
        setCADefinedFreshestCRL(info.getCADefinedFreshestCRL());
        setDefaultOCSPServiceLocator(info.getDefaultOCSPServiceLocator());
        setUseUTF8PolicyText(info.getUseUTF8PolicyText());
        setUsePrintableStringSubjectDN(info.getUsePrintableStringSubjectDN());
        setUseLdapDNOrder(info.getUseLdapDnOrder());
        setUseCrlDistributionPointOnCrl(info.getUseCrlDistributionPointOnCrl());
        setCrlDistributionPointOnCrlCritical(info.getCrlDistributionPointOnCrlCritical());
        setCmpRaAuthSecret(info.getCmpRaAuthSecret());
    }
    
    /**
     * Allows updating of fields that are otherwise not changeable in existing CAs.
     */
    @Override
    public void updateUninitializedCA(CAInfo cainfo) {
        super.updateUninitializedCA(cainfo);
        X509CAInfo info = (X509CAInfo) cainfo;
        data.put(SUBJECTALTNAME, info.getSubjectAltName());
        data.put(POLICIES, info.getPolicies());
    }

    @Override
    public byte[] createPKCS7(CryptoToken cryptoToken, Certificate cert, boolean includeChain) throws SignRequestSignatureException {
        // First verify that we signed this certificate
        try {
            if (cert != null) {
                final PublicKey verifyKey;
                final X509Certificate cacert = (X509Certificate)getCACertificate();
                if (cacert != null) {
                    verifyKey = cacert.getPublicKey();
                } else {
                    verifyKey = cryptoToken.getPublicKey(getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
                }
               cert.verify(verifyKey);
            }
        } catch (Exception e) {
            throw new SignRequestSignatureException("Cannot verify certificate in createPKCS7(), did I sign this?");
        }
        Collection<Certificate> chain = getCertificateChain();
        ArrayList<Certificate> certList = new ArrayList<Certificate>();
        if (cert != null) {
            certList.add(cert);
        }
        if (includeChain) {
            certList.addAll(chain);
        }
        try {
            CMSProcessable msg = new CMSProcessableByteArray("EJBCA".getBytes());
            CertStore certs = CertStore.getInstance("Collection", new CollectionCertStoreParameters(certList), "BC");
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            final PrivateKey privateKey = cryptoToken.getPrivateKey(getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
            if (privateKey == null) {
                String msg1 = "createPKCS7: Private key does not exist!";
                log.debug(msg1);
                throw new SignRequestSignatureException(msg1);
            }
            gen.addSigner(privateKey, (X509Certificate) getCACertificate(), CMSSignedGenerator.DIGEST_SHA1);
            gen.addCertificatesAndCRLs(certs);
            CMSSignedData s = null;
            CAToken catoken = getCAToken();
            if (catoken != null && !(cryptoToken instanceof NullCryptoToken)) {
                log.debug("createPKCS7: Provider=" + cryptoToken.getSignProviderName() + " using algorithm "
                        + privateKey.getAlgorithm());
                s = gen.generate(msg, true, cryptoToken.getSignProviderName());
            } else {
                String msg1 = "CA Token does not exist!";
                log.debug(msg);
                throw new SignRequestSignatureException(msg1);
            }
            return s.getEncoded();
        } catch (CryptoTokenOfflineException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see CA#createRequest(Collection, String, Certificate, int)
     */
    @Override
    public byte[] createRequest(CryptoToken cryptoToken, Collection<ASN1Encodable> attributes, String signAlg, Certificate cacert, int signatureKeyPurpose)
            throws CryptoTokenOfflineException {
        log.trace(">createRequest: " + signAlg + ", " + CertTools.getSubjectDN(cacert) + ", " + signatureKeyPurpose);
        ASN1Set attrset = new DERSet();
        if (attributes != null) {
            log.debug("Adding attributes in the request");
            Iterator<ASN1Encodable> iter = attributes.iterator();
            ASN1EncodableVector vec = new ASN1EncodableVector();
            while (iter.hasNext()) {
                ASN1Encodable o = (ASN1Encodable) iter.next();
                vec.add(o);
            }
            attrset = new DERSet(vec);
        }
        X509NameEntryConverter converter = null;
        if (getUsePrintableStringSubjectDN()) {
            converter = new PrintableStringEntryConverter();
        } else {
            converter = new X509DefaultEntryConverter();
        }
        X500Name x509dn = CertTools.stringToBcX500Name(getSubjectDN(), converter, getUseLdapDNOrder());
        PKCS10CertificationRequest req;
        try {
            final CAToken catoken = getCAToken();
            final String alias = catoken.getAliasFromPurpose(signatureKeyPurpose);
            final KeyPair keyPair = new KeyPair(cryptoToken.getPublicKey(alias), cryptoToken.getPrivateKey(alias));
            req = CertTools.genPKCS10CertificationRequest(signAlg, x509dn, keyPair.getPublic(), attrset, keyPair.getPrivate(), cryptoToken.getSignProviderName());
            log.trace("<createRequest");
            return req.getEncoded();
        } catch (CryptoTokenOfflineException e) { // NOPMD, since we catch wide below
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * If request is an CA certificate, useprevious==true and createlinkcert==true it returns a new certificate signed with the CAs keys. This can be
     * used to create a NewWithOld certificate for CA key rollover. This method can only create a self-signed certificate and only uses the public key
     * from the passed in certificate. If the passed in certificate is not signed by the CAs signature key and does not have the same DN as the
     * current CA, null certificate is returned. This is because we do not want to create anything else than a NewWithOld certificate, because that
     * would be a security risk. Regular certificates must be issued using createCertificate.
     * 
     * Note: Creating the NewWithOld will only work correctly for Root CAs.
     * 
     * If request is a CSR (pkcs10) it returns null.
     * 
     * @param usepreviouskey
     *            must be trust otherwise null is returned, this is because this method on an X509CA should only be used to create a NewWithOld.
     * 
     * @see CA#signRequest(Collection, String)
     */
    @Override
    public byte[] createAuthCertSignRequest(CryptoToken cryptoToken, final byte[] request) throws CryptoTokenOfflineException {
        throw new UnsupportedOperationException("Creation of authenticated CSRs is not supported for X509 CAs.");
    }

    @Override
    public void createOrRemoveLinkCertificate(final CryptoToken cryptoToken, final boolean createLinkCertificate, final CertificateProfile certProfile) throws CryptoTokenOfflineException {
        byte[] ret = null;
        if (createLinkCertificate) {
            try {
                final CAToken catoken = getCAToken();
                // Check if the input was a CA certificate, which is the same CA as this. If all is true we should create a NewWithOld link-certificate
                final X509Certificate currentCaCert = (X509Certificate) getCACertificate();
                if (log.isDebugEnabled()) {
                    log.debug("We will create a link certificate.");
                }
                final X509CAInfo info = (X509CAInfo) getCAInfo();
                final EndEntityInformation cadata = new EndEntityInformation("nobody", info.getSubjectDN(), info.getSubjectDN().hashCode(), info.getSubjectAltName(), null,
                        0, new EndEntityType(EndEntityTypes.INVALID), 0, info.getCertificateProfileId(), null, null, 0, 0, null);
                final PublicKey previousCaPublicKey = cryptoToken.getPublicKey(catoken.getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN_PREVIOUS));
                final PrivateKey previousCaPrivateKey = cryptoToken.getPrivateKey(catoken.getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN_PREVIOUS));
                final String provider = cryptoToken.getSignProviderName();
                // The sequence is ignored later, but we fetch the same previous for now to do this the same way as for CVC..
                final String ignoredKeySequence = catoken.getProperties().getProperty(CATokenConstants.PREVIOUS_SEQUENCE_PROPERTY);
                final Certificate retcert = generateCertificate(cadata, null, currentCaCert.getPublicKey(), -1, currentCaCert.getNotBefore(), currentCaCert.getNotAfter(),
                        certProfile, null, ignoredKeySequence, previousCaPublicKey, previousCaPrivateKey, provider);
                log.info(intres.getLocalizedMessage("cvc.info.createlinkcert", cadata.getDN(), cadata.getDN()));
                ret = retcert.getEncoded();
            } catch (CryptoTokenOfflineException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Bad CV CA certificate.", e);
            }
        }
        updateLatestLinkCertificate(ret);
    }

    @Override
    public Certificate generateCertificate(CryptoToken cryptoToken, final EndEntityInformation subject, final RequestMessage request, final PublicKey publicKey, final int keyusage, final Date notBefore,
            final Date notAfter, final CertificateProfile certProfile, final Extensions extensions, final String sequence) throws Exception {
        // Before we start, check if the CA is off-line, we don't have to waste time
        // one the stuff below of we are off-line. The line below will throw CryptoTokenOfflineException of CA is offline
        final CAToken catoken = getCAToken();
        final PublicKey caPublicKey = cryptoToken.getPublicKey(catoken.getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        final PrivateKey caPrivateKey = cryptoToken.getPrivateKey(catoken.getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        final String provider = cryptoToken.getSignProviderName();
        return generateCertificate(subject, request, publicKey, keyusage, notBefore, notAfter, certProfile, extensions, sequence,
                caPublicKey, caPrivateKey, provider);
    }

    /**
     * sequence is ignored by X509CA
     */
    private Certificate generateCertificate(final EndEntityInformation subject, final RequestMessage request, final PublicKey publicKey, final int keyusage, final Date notBefore,
            final Date notAfter, final CertificateProfile certProfile, final Extensions extensions, final String sequence, final PublicKey caPublicKey,
            final PrivateKey caPrivateKey, final String provider) throws Exception {

        // We must only allow signing to take place if the CA itself is on line, even if the token is on-line.
        // We have to allow expired as well though, so we can renew expired CAs
        if ((getStatus() != CAConstants.CA_ACTIVE) && ((getStatus() != CAConstants.CA_EXPIRED))) {
            final String msg = intres.getLocalizedMessage("error.caoffline", getName(), getStatus());
            if (log.isDebugEnabled()) {
                log.debug(msg); // This is something we handle so no need to log with higher priority
            }
            throw new CAOfflineException(msg);
        }

        final String sigAlg;
        if (certProfile.getSignatureAlgorithm() == null) {
            sigAlg = getCAToken().getSignatureAlgorithm();
        } else {
            sigAlg = certProfile.getSignatureAlgorithm();
        }
        // Check that the signature algorithm is one of the allowed ones
        if (!ArrayUtils.contains(AlgorithmConstants.AVAILABLE_SIGALGS, sigAlg)) {
            final String msg = intres.getLocalizedMessage("createcert.invalidsignaturealg", sigAlg);
            throw new InvalidAlgorithmException(msg);        	
        }
        // Check if this is a root CA we are creating
        final boolean isRootCA = certProfile.getType() == CertificateConstants.CERTTYPE_ROOTCA;

        final X509Certificate cacert = (X509Certificate) getCACertificate();
        // Check CA certificate PrivateKeyUsagePeriod if it exists (throws CAOfflineException if it exists and is not within this time)
        CertificateValidity.checkPrivateKeyUsagePeriod(cacert);
        // Get certificate validity time notBefore and notAfter
        final CertificateValidity val = new CertificateValidity(subject, certProfile, notBefore, notAfter, cacert, isRootCA);

        final BigInteger serno;
        {
            // Serialnumber is either random bits, where random generator is initialized by the serno generator.
            // Or a custom serial number defined in the end entity object
            final ExtendedInformation ei = subject.getExtendedinformation();
            if (certProfile.getAllowCertSerialNumberOverride()) {
                serno = (ei != null ? ei.certificateSerialNumber() : SernoGeneratorRandom.instance().getSerno());
            } else {
                serno = SernoGeneratorRandom.instance().getSerno();
                if ((ei != null) && (ei.certificateSerialNumber() != null)) {
                    final String msg = intres.getLocalizedMessage("createcert.certprof_not_allowing_cert_sn_override_using_normal", ei.certificateSerialNumber().toString(16));
                    log.info(msg);
                }
            }
        }

        // Make DNs
        String dn = subject.getCertificateDN();
        if (certProfile.getUseSubjectDNSubSet()) {
            dn = certProfile.createSubjectDNSubSet(dn);
        }

        if (certProfile.getUseCNPostfix()) {
            dn = CertTools.insertCNPostfix(dn, certProfile.getCNPostfix());
        }

        final X509NameEntryConverter converter;
        if (getUsePrintableStringSubjectDN()) {
            converter = new PrintableStringEntryConverter();
        } else {
            converter = new X509DefaultEntryConverter();
        }
        // Will we use LDAP DN order (CN first) or X500 DN order (CN last) for the subject DN
        final boolean ldapdnorder;
        if ((getUseLdapDNOrder() == false) || (certProfile.getUseLdapDnOrder() == false)) {
            ldapdnorder = false;
        } else {
            ldapdnorder = true;
        }
        final X500Name subjectDNName;
        if (certProfile.getAllowDNOverride() && (request != null) && (request.getRequestX500Name() != null)) {
            subjectDNName = request.getRequestX500Name();
            if (log.isDebugEnabled()) {
                log.debug("Using X509Name from request instead of user's registered.");
            }
        } else {
            subjectDNName = CertTools.stringToBcX500Name(dn, converter, ldapdnorder);
        }
        // Make sure the DN does not contain dangerous characters
        if (StringTools.hasStripChars(subjectDNName.toString())) {
            if (log.isTraceEnabled()) {
            	log.trace("DN with illegal name: "+subjectDNName);
            }
            final String msg = intres.getLocalizedMessage("createcert.illegalname");
        	throw new IllegalNameException(msg);
        }
        if (log.isDebugEnabled()) {
            log.debug("Using subjectDN: " + subjectDNName.toString());
        }

        // We must take the issuer DN directly from the CA-certificate otherwise we risk re-ordering the DN
        // which many applications do not like.
        X500Name issuerDNName;
        if (isRootCA) {
            // This will be an initial root CA, since no CA-certificate exists
            // Or it is a root CA, since the cert is self signed. If it is a root CA we want to use the same encoding for subject and issuer,
            // it might have changed over the years.
            if (log.isDebugEnabled()) {
                log.debug("Using subject DN also as issuer DN, because it is a root CA");
            }
            issuerDNName = subjectDNName;
        } else {
            issuerDNName = X500Name.getInstance(cacert.getSubjectX500Principal().getEncoded());
            if (log.isDebugEnabled()) {
                log.debug("Using issuer DN directly from the CA certificate: " + issuerDNName.toString());
            }
        }

        SubjectPublicKeyInfo pkinfo = new SubjectPublicKeyInfo((ASN1Sequence)ASN1Primitive.fromByteArray(publicKey.getEncoded()));
        final X509v3CertificateBuilder certbuilder = new X509v3CertificateBuilder(issuerDNName, serno, val.getNotBefore(), val.getNotAfter(), subjectDNName, pkinfo);
        
        //
        // X509 Certificate Extensions
        //

        // Extensions we will add to the certificate, later when we have filled the structure with
        // everything we want.
        final ExtensionsGenerator extgen = new ExtensionsGenerator();

        // First we check if there is general extension override, and add all extensions from
        // the request in that case
        if (certProfile.getAllowExtensionOverride() && extensions != null) {
            ASN1ObjectIdentifier[] oids = extensions.getExtensionOIDs();
            for(ASN1ObjectIdentifier oid : oids ) {
                final Extension ext = extensions.getExtension(oid);
                if (log.isDebugEnabled()) {
                    log.debug("Overriding extension with oid: " + oid);
                }
                    extgen.addExtension(oid, ext.isCritical(), ext.getParsedValue());
            }
        }

        // Second we see if there is Key usage override
        Extensions overridenexts = extgen.generate();
        if (certProfile.getAllowKeyUsageOverride() && (keyusage >= 0)) {
            if (log.isDebugEnabled()) {
                log.debug("AllowKeyUsageOverride=true. Using KeyUsage from parameter: " + keyusage);
            }
            if ((certProfile.getUseKeyUsage() == true) && (keyusage >= 0)) {
                final KeyUsage ku = new KeyUsage(keyusage);
                // We don't want to try to add custom extensions with the same oid if we have already added them
                // from the request, if AllowExtensionOverride is enabled.
                // Two extensions with the same oid is not allowed in the standard.
                if (overridenexts.getExtension(Extension.keyUsage) == null) {
                    extgen.addExtension(Extension.keyUsage, certProfile.getKeyUsageCritical(), ku);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("KeyUsage was already overridden by an extension, not using KeyUsage from parameter.");
                    }
                }
            }
        }

        // Third, check for standard Certificate Extensions that should be added.
        // Standard certificate extensions are defined in CertificateProfile and CertificateExtensionFactory
        // and implemented in package org.ejbca.core.model.certextensions.standard
        final CertificateExtensionFactory fact = CertificateExtensionFactory.getInstance();
        final List<String> usedStdCertExt = certProfile.getUsedStandardCertificateExtensions();
        final Iterator<String> certStdExtIter = usedStdCertExt.iterator();
        overridenexts = extgen.generate();
        while (certStdExtIter.hasNext()) {
            final String oid = certStdExtIter.next();
            // We don't want to try to add standard extensions with the same oid if we have already added them
            // from the request, if AllowExtensionOverride is enabled.
            // Two extensions with the same oid is not allowed in the standard.
            if (overridenexts.getExtension(new ASN1ObjectIdentifier(oid)) == null) {
                final CertificateExtension certExt = fact.getStandardCertificateExtension(oid, certProfile);
                if (certExt != null) {
                    final byte[] value = certExt.getValueEncoded(subject, this, certProfile, publicKey, caPublicKey, val);
                    if (value != null) {
                        extgen.addExtension(new ASN1ObjectIdentifier(certExt.getOID()), certExt.isCriticalFlag(), value);
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Extension with oid " + oid + " has been overridden, standard extension will not be added.");
                }
            }
        }

        // Fourth, check for custom Certificate Extensions that should be added.
        // Custom certificate extensions is defined in certextensions.properties
        final List<Integer> usedCertExt = certProfile.getUsedCertificateExtensions();
        final Iterator<Integer> certExtIter = usedCertExt.iterator();
        while (certExtIter.hasNext()) {
            final Integer id = certExtIter.next();
            final CertificateExtension certExt = fact.getCertificateExtensions(id);
            if (certExt != null) {
                // We don't want to try to add custom extensions with the same oid if we have already added them
                // from the request, if AllowExtensionOverride is enabled.
                // Two extensions with the same oid is not allowed in the standard.
                if (overridenexts.getExtension(new ASN1ObjectIdentifier(certExt.getOID())) == null) {
                    final byte[] value = certExt.getValueEncoded(subject, this, certProfile, publicKey, caPublicKey, val);
                    if (value != null) {
                        extgen.addExtension(new ASN1ObjectIdentifier(certExt.getOID()), certExt.isCriticalFlag(), value);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Extension with oid " + certExt.getOID() + " has been overridden, custom extension will not be added.");
                    }
                }
            }
        }

        // Finally add extensions to certificate generator
        final Extensions exts = extgen.generate();
        ASN1ObjectIdentifier[] oids = exts.getExtensionOIDs();
        for(ASN1ObjectIdentifier oid : oids) {
            final Extension ext = exts.getExtension(oid);
            certbuilder.addExtension(oid, ext.isCritical(), ext.getParsedValue());
        }

        //
        // End of extensions
        //

        if (log.isTraceEnabled()) {
            log.trace(">certgen.generate");
        }
        final ContentSigner signer = new BufferingContentSigner(new JcaContentSignerBuilder(sigAlg).setProvider(provider).build(caPrivateKey), 20480);
        final X509CertificateHolder certHolder = certbuilder.build(signer);
        final X509Certificate cert = (X509Certificate)CertTools.getCertfromByteArray(certHolder.getEncoded());
        if (log.isTraceEnabled()) {
            log.trace("<certgen.generate");
        }

        // Verify using the CA certificate before returning
        // If we can not verify the issued certificate using the CA certificate we don't want to issue this cert
        // because something is wrong...
        final PublicKey verifyKey;
        // We must use the configured public key if this is a rootCA, because then we can renew our own certificate, after changing
        // the keys. In this case the _new_ key will not match the current CA certificate.
        if ((cacert != null) && (!isRootCA)) {
            verifyKey = cacert.getPublicKey();
        } else {
            verifyKey = caPublicKey;
        }
        cert.verify(verifyKey);

        // If we have a CA-certificate, verify that we have all path verification stuff correct
        if (cacert != null) {
            final byte[] aki = CertTools.getAuthorityKeyId(cert);
            final byte[] ski = CertTools.getSubjectKeyId(isRootCA ? cert : cacert);
            if ((aki != null) && (ski != null)) {
                final boolean eq = Arrays.equals(aki, ski);
                if (!eq) {
                    final String akistr = new String(Hex.encode(aki));
                    final String skistr = new String(Hex.encode(ski));
                    final String msg = intres.getLocalizedMessage("createcert.errorpathverifykeyid", akistr, skistr);
                    log.error(msg);
                    // This will differ if we create link certificates, NewWithOld, therefore we can not throw an exception here.
                }
            }
            final Principal issuerDN = cert.getIssuerX500Principal();
            final Principal subjectDN = cacert.getSubjectX500Principal();
            if ((issuerDN != null) && (subjectDN != null)) {
                final boolean eq = issuerDN.equals(subjectDN);
                if (!eq) {
                	final String msg = intres.getLocalizedMessage("createcert.errorpathverifydn", issuerDN.getName(), subjectDN.getName());
                    log.error(msg);
                    throw new CertificateCreateException(msg);
                }
            }
        }
        // Before returning from this method, we will set the private key and provider in the request message, in case the response  message needs to be signed
        if (request != null) {
            request.setResponseKeyInfo(caPrivateKey, provider);
        }
        if (log.isDebugEnabled()) {
            log.debug("X509CA: generated certificate, CA " + this.getCAId() + " for DN: " + subject.getCertificateDN());
        }
        return cert;
    }

    @Override
    public X509CRLHolder generateCRL(CryptoToken cryptoToken, Collection<RevokedCertInfo> certs, int crlnumber) throws CryptoTokenOfflineException, IllegalCryptoTokenException,
            IOException, SignatureException, NoSuchProviderException, InvalidKeyException, CRLException, NoSuchAlgorithmException {
        return generateCRL(cryptoToken, certs, getCRLPeriod(), crlnumber, false, 0);
    }

    @Override
    public X509CRLHolder generateDeltaCRL(CryptoToken cryptoToken, Collection<RevokedCertInfo> certs, int crlnumber, int basecrlnumber) throws CryptoTokenOfflineException,
            IllegalCryptoTokenException, IOException, SignatureException, NoSuchProviderException, InvalidKeyException, CRLException,
            NoSuchAlgorithmException {
        return generateCRL(cryptoToken, certs, getDeltaCRLPeriod(), crlnumber, true, basecrlnumber);
    }

    /**
     * Generate a CRL or a deltaCRL
     * 
     * @param certs
     *            list of revoked certificates
     * @param crlnumber
     *            CRLNumber for this CRL
     * @param isDeltaCRL
     *            true if we should generate a DeltaCRL
     * @param basecrlnumber
     *            caseCRLNumber for a delta CRL, use 0 for full CRLs
     * @param certProfile
     *            certificate profile for CRL Distribution point in the CRL, or null
     * @return CRL
     * @throws CryptoTokenOfflineException
     * @throws IllegalCryptoTokenException
     * @throws IOException
     * @throws SignatureException
     * @throws NoSuchProviderException
     * @throws InvalidKeyException
     * @throws CRLException
     * @throws NoSuchAlgorithmException
     */
    private X509CRLHolder generateCRL(CryptoToken cryptoToken, Collection<RevokedCertInfo> certs, long crlPeriod, int crlnumber, boolean isDeltaCRL, int basecrlnumber)
            throws CryptoTokenOfflineException, IllegalCryptoTokenException, IOException, SignatureException, NoSuchProviderException,
            InvalidKeyException, CRLException, NoSuchAlgorithmException {
        final String sigAlg = getCAInfo().getCAToken().getSignatureAlgorithm();

        if (log.isDebugEnabled()) {
            log.debug("generateCRL(" + certs.size() + ", " + crlPeriod + ", " + crlnumber + ", " + isDeltaCRL + ", " + basecrlnumber);
        }

        // Make DNs
        final X509Certificate cacert = (X509Certificate) getCACertificate();
        final X500Name issuer;
        if (cacert == null) {
            // This is an initial root CA, since no CA-certificate exists
            // (I don't think we can ever get here!!!)
            final X509NameEntryConverter converter;
            if (getUsePrintableStringSubjectDN()) {
                converter = new PrintableStringEntryConverter();
            } else {
                converter = new X509DefaultEntryConverter();
            }

            issuer = CertTools.stringToBcX500Name(getSubjectDN(), converter, getUseLdapDNOrder());
        } else {
            issuer = X500Name.getInstance(cacert.getSubjectX500Principal().getEncoded());
        }
        final Date thisUpdate = new Date();
        final Date nextUpdate = new Date();
        nextUpdate.setTime(nextUpdate.getTime() + crlPeriod);
        final X509v2CRLBuilder crlgen = new X509v2CRLBuilder(issuer, thisUpdate);
        crlgen.setNextUpdate(nextUpdate);
        if (certs != null) {
            if (log.isDebugEnabled()) {
                log.debug("Adding "+certs.size()+" revoked certificates to CRL. Free memory="+Runtime.getRuntime().freeMemory());
            }          
            final Iterator<RevokedCertInfo> it = certs.iterator();
            while (it.hasNext()) {
                final RevokedCertInfo certinfo = (RevokedCertInfo) it.next();
                crlgen.addCRLEntry(certinfo.getUserCertificate(), certinfo.getRevocationDate(), certinfo.getReason());
            }
            if (log.isDebugEnabled()) {
                log.debug("Finished adding "+certs.size()+" revoked certificates to CRL. Free memory="+Runtime.getRuntime().freeMemory());
            }          
        }

             
        // Authority key identifier
        if (getUseAuthorityKeyIdentifier() == true) {      
            ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(cryptoToken.getPublicKey(
                    getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CRLSIGN)).getEncoded()));
            try {
                SubjectPublicKeyInfo apki = new SubjectPublicKeyInfo((ASN1Sequence) asn1InputStream.readObject());
                AuthorityKeyIdentifier aki = new AuthorityKeyIdentifier(apki);
                crlgen.addExtension(Extension.authorityKeyIdentifier, getAuthorityKeyIdentifierCritical(), aki);
            } finally {
                asn1InputStream.close();
            }
        }
        
        // Authority Information Access  
        final ASN1EncodableVector accessList = new ASN1EncodableVector();
        if (getAuthorityInformationAccess() != null) {
            for(String url :  getAuthorityInformationAccess()) {   
                if(StringUtils.isNotEmpty(url)) {
                    GeneralName accessLocation = new GeneralName(GeneralName.uniformResourceIdentifier, new DERIA5String(url));
                    accessList.add(new AccessDescription(AccessDescription.id_ad_caIssuers, accessLocation));
                }
            }               
        }
        if(accessList.size() > 0) {
            AuthorityInformationAccess authorityInformationAccess = AuthorityInformationAccess.getInstance(new DERSequence(accessList));
            // "This CRL extension MUST NOT be marked critical." according to rfc4325
            crlgen.addExtension(Extension.authorityInfoAccess, false, authorityInformationAccess);
        }
                
        // CRLNumber extension
        if (getUseCRLNumber() == true) {
            CRLNumber crlnum = new CRLNumber(BigInteger.valueOf(crlnumber));
            crlgen.addExtension(Extension.cRLNumber, this.getCRLNumberCritical(), crlnum);
        }

        if (isDeltaCRL) {
            // DeltaCRLIndicator extension
            CRLNumber basecrlnum = new CRLNumber(BigInteger.valueOf(basecrlnumber));
            crlgen.addExtension(Extension.deltaCRLIndicator, true, basecrlnum);
        }
        // CRL Distribution point URI and Freshest CRL DP
        if (getUseCrlDistributionPointOnCrl()) {
            String crldistpoint = getDefaultCRLDistPoint();
            List<DistributionPoint> distpoints = generateDistributionPoints(crldistpoint);

            if (distpoints.size() > 0) {
                IssuingDistributionPoint idp = new IssuingDistributionPoint(distpoints.get(0).getDistributionPoint(), false, false, null, false,
                        false);

                // According to the RFC, IDP must be a critical extension.
                // Nonetheless, at the moment, Mozilla is not able to correctly
                // handle the IDP extension and discards the CRL if it is critical.
                crlgen.addExtension(Extension.issuingDistributionPoint, getCrlDistributionPointOnCrlCritical(), idp);
            }

            if (!isDeltaCRL) {
                String crlFreshestDP = getCADefinedFreshestCRL();
                List<DistributionPoint> freshestDistPoints = generateDistributionPoints(crlFreshestDP);
                if (freshestDistPoints.size() > 0) {
                    CRLDistPoint ext = new CRLDistPoint((DistributionPoint[]) freshestDistPoints.toArray(new DistributionPoint[freshestDistPoints
                            .size()]));

                    // According to the RFC, the Freshest CRL extension on a
                    // CRL must not be marked as critical. Therefore it is
                    // hardcoded as not critical and is independent of
                    // getCrlDistributionPointOnCrlCritical().
                    crlgen.addExtension(Extension.freshestCRL, false, ext);
                }

            }
        }

        final X509CRLHolder crl;
        if (log.isDebugEnabled()) {
            log.debug("Signing CRL. Free memory="+Runtime.getRuntime().freeMemory());
        }
        final String alias = getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CRLSIGN);
        try {
            final ContentSigner signer = new BufferingContentSigner(new JcaContentSignerBuilder(sigAlg).setProvider(cryptoToken.getSignProviderName()).build(cryptoToken.getPrivateKey(alias)), 20480);
            crl = crlgen.build(signer);
        } catch (OperatorCreationException e) {
            // Very fatal error
            throw new RuntimeException("Can not create Jca content signer: ", e);
        }
        if (log.isDebugEnabled()) {
            log.debug("Finished signing CRL. Free memory="+Runtime.getRuntime().freeMemory());
        }          
        
        // Verify using the CA certificate before returning
        // If we can not verify the issued CRL using the CA certificate we don't want to issue this CRL
        // because something is wrong...
        final PublicKey verifyKey;
        if (cacert != null) {
            verifyKey = cacert.getPublicKey();
            if (log.isTraceEnabled()) {
                log.trace("Got the verify key from the CA certificate.");
            }
        } else {
            verifyKey = cryptoToken.getPublicKey(alias);
            if (log.isTraceEnabled()) {
                log.trace("Got the verify key from the CA token.");
            }
        }
        try {
            final ContentVerifierProvider verifier = new JcaContentVerifierProviderBuilder().build(verifyKey);
            if (!crl.isSignatureValid(verifier)) {
                throw new SignatureException("Error verifying CRL to be returned.");
            }
        } catch (OperatorCreationException e) {
            // Very fatal error
            throw new RuntimeException("Can not create Jca content signer: ", e);
        } catch (CertException e) {
            throw new SignatureException(e.getMessage(), e);
        }
        if (log.isDebugEnabled()) {
            log.debug("Returning CRL. Free memory="+Runtime.getRuntime().freeMemory());
        }          
        return crl;
    }

    /**
     * Generate a list of Distribution points.
     * 
     * @param distPoints
     *            distribution points as String in semi column (';') separated format.
     * @return list of distribution points.
     */
    private List<DistributionPoint> generateDistributionPoints(String distPoints) {
        if (distPoints == null) {
            distPoints = "";
        }
        // Multiple CDPs are separated with the ';' sign
        Iterator<String> it = StringTools.splitURIs(distPoints).iterator();
        ArrayList<DistributionPoint> result = new ArrayList<DistributionPoint>();
        while (it.hasNext()) {
            String uri = (String) it.next();
            GeneralName gn = new GeneralName(GeneralName.uniformResourceIdentifier, new DERIA5String(uri));
            if (log.isDebugEnabled()) {
                log.debug("Added CRL distpoint: " + uri);
            }
            ASN1EncodableVector vec = new ASN1EncodableVector();
            vec.add(gn);
            GeneralNames gns = GeneralNames.getInstance(new DERSequence(vec));
            DistributionPointName dpn = new DistributionPointName(0, gns);
            result.add(new DistributionPoint(dpn, null, null));
        }
        return result;
    }

    /** Implementation of UpgradableDataHashMap function getLatestVersion */
    public float getLatestVersion() {
        return LATEST_VERSION;
    }

    /**
     * Implementation of UpgradableDataHashMap function upgrade.
     */
    public void upgrade() {
        if (Float.compare(LATEST_VERSION, getVersion()) != 0) {
            // New version of the class, upgrade
            log.info("Upgrading X509CA with version " + getVersion());
            if (data.get(DEFAULTOCSPSERVICELOCATOR) == null) {
                setDefaultCRLDistPoint("");
                setDefaultOCSPServiceLocator("");
            }
            if (data.get(CRLISSUEINTERVAL) == null) {
                setCRLIssueInterval(0);
            }
            if (data.get(CRLOVERLAPTIME) == null) {
                // Default value 10 minutes
                setCRLOverlapTime(10);
            }
            boolean useprintablestring = true;
            if (data.get("alwaysuseutf8subjectdn") == null) {
                // Default value false
                if (data.get(USEUTF8POLICYTEXT) == null) {
                    setUseUTF8PolicyText(false);
                }
            } else {
                // Use the same value as we had before when we had alwaysuseutf8subjectdn
                boolean useutf8 = ((Boolean) data.get("alwaysuseutf8subjectdn")).booleanValue();
                if (data.get(USEUTF8POLICYTEXT) == null) {
                    setUseUTF8PolicyText(useutf8);
                }
                // If we had checked to use utf8 on an old CA, we do not want to use PrintableString after upgrading
                useprintablestring = !useutf8;
            }
            if (data.get(USEPRINTABLESTRINGSUBJECTDN) == null) {
                // Default value true (as before)
                setUsePrintableStringSubjectDN(useprintablestring);
            }
            if (data.get(DEFAULTCRLISSUER) == null) {
                setDefaultCRLIssuer(null);
            }
            if (data.get(USELDAPDNORDER) == null) {
                setUseLdapDNOrder(true); // Default value
            }
            if (data.get(DELTACRLPERIOD) == null) {
                setDeltaCRLPeriod(0); // v14
            }
            if (data.get(USECRLDISTRIBUTIONPOINTONCRL) == null) {
                setUseCrlDistributionPointOnCrl(false); // v15
            }
            if (data.get(CRLDISTRIBUTIONPOINTONCRLCRITICAL) == null) {
                setCrlDistributionPointOnCrlCritical(false); // v15
            }
            if (data.get(INCLUDEINHEALTHCHECK) == null) {
                setIncludeInHealthCheck(true); // v16
            }
            // v17->v18 is only an upgrade in order to upgrade CA token
            // v18->v19
            Object o = data.get(CRLPERIOD);
            if (o instanceof Integer) {
                setCRLPeriod(((Integer) o).longValue() * SimpleTime.MILLISECONDS_PER_HOUR); // h to ms
            }
            o = data.get(CRLISSUEINTERVAL);
            if (o instanceof Integer) {
                setCRLIssueInterval(((Integer) o).longValue() * SimpleTime.MILLISECONDS_PER_HOUR); // h to ms
            }
            o = data.get(CRLOVERLAPTIME);
            if (o instanceof Integer) {
                setCRLOverlapTime(((Integer) o).longValue() * SimpleTime.MILLISECONDS_PER_MINUTE); // min to ms
            }
            o = data.get(DELTACRLPERIOD);
            if (o instanceof Integer) {
                setDeltaCRLPeriod(((Integer) o).longValue() * SimpleTime.MILLISECONDS_PER_HOUR); // h to ms
            }
            data.put(VERSION, new Float(LATEST_VERSION));
        }
    }

    /**
     * Method to upgrade new (or existing external caservices) This method needs to be called outside the regular upgrade since the CA isn't
     * instantiated in the regular upgrade.
     */
    @SuppressWarnings({ "rawtypes", "deprecation" })
    public boolean upgradeExtendedCAServices() {
        boolean retval = false;
        // call upgrade, if needed, on installed CA services
        Collection<Integer> externalServiceTypes = getExternalCAServiceTypes();
        if (!CesecoreConfiguration.getCaKeepOcspExtendedService() && externalServiceTypes.contains(ExtendedCAServiceTypes.TYPE_OCSPEXTENDEDSERVICE)) {
            //This type has been removed, so remove it from any CAs it's been added to as well.
            externalServiceTypes.remove(ExtendedCAServiceTypes.TYPE_OCSPEXTENDEDSERVICE);
            data.put(EXTENDEDCASERVICES, externalServiceTypes);
            retval = true;
        }
        
        for (Integer type : externalServiceTypes) {
            ExtendedCAService service = getExtendedCAService(type);
            if (service != null) {
                if (Float.compare(service.getLatestVersion(), service.getVersion()) != 0) {
                    retval = true;
                    service.upgrade();
                    setExtendedCAServiceData(service.getExtendedCAServiceInfo().getType(), (HashMap) service.saveData());
                } else if (service.isUpgraded()) {
                    // Also return true if the service was automatically upgraded by a UpgradeableDataHashMap.load, which calls upgrade automagically. 
                    retval = true;
                    setExtendedCAServiceData(service.getExtendedCAServiceInfo().getType(), (HashMap) service.saveData());
                }
            } else {
                log.error("Extended service is null, can not upgrade service of type: " + type);
            }
        }
        return retval;
    }

    @Override
    public byte[] encryptKeys(CryptoToken cryptoToken, KeyPair keypair) throws IOException, CryptoTokenOfflineException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(baos);
        os.writeObject(keypair);

        CMSEnvelopedDataGenerator edGen = new CMSEnvelopedDataGenerator();

        CMSEnvelopedData ed;
        try {
            edGen.addKeyTransRecipient(cryptoToken.getPublicKey(getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_KEYENCRYPT)), this.keyId);
            ed = edGen.generate(new CMSProcessableByteArray(baos.toByteArray()), CMSEnvelopedDataGenerator.AES256_CBC, "BC");
        } catch (Exception e) {
            log.error("-encryptKeys: ", e);
            throw new IOException(e.getMessage());
        }

        return ed.getEncoded();
    }

    @Override
    public KeyPair decryptKeys(CryptoToken cryptoToken, byte[] data) throws Exception {
        CMSEnvelopedData ed = new CMSEnvelopedData(data);

        RecipientInformationStore recipients = ed.getRecipientInfos();
        RecipientInformation recipient = (RecipientInformation) recipients.getRecipients().iterator().next();
        ObjectInputStream ois = null;
        JceKeyTransEnvelopedRecipient rec = new JceKeyTransEnvelopedRecipient(cryptoToken.getPrivateKey(getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_KEYENCRYPT)));
        rec.setProvider(cryptoToken.getEncProviderName());
        rec.setContentProvider("BC");
        byte[] recdata = recipient.getContent(rec);
        ois = new ObjectInputStream(new ByteArrayInputStream(recdata));

        return (KeyPair) ois.readObject();
    }

    @Override
    public byte[] decryptData(CryptoToken cryptoToken, byte[] data, int cAKeyPurpose) throws Exception {
        CMSEnvelopedData ed = new CMSEnvelopedData(data);
        RecipientInformationStore recipients = ed.getRecipientInfos();
        RecipientInformation recipient = (RecipientInformation) recipients.getRecipients().iterator().next();
        JceKeyTransEnvelopedRecipient rec = new JceKeyTransEnvelopedRecipient(cryptoToken.getPrivateKey(getCAToken().getAliasFromPurpose(cAKeyPurpose)));
        rec.setProvider(cryptoToken.getSignProviderName());
        rec.setContentProvider("BC");
        byte[] recdata = recipient.getContent(rec);
        return recdata;
    }

    @Override
    public byte[] encryptData(CryptoToken cryptoToken, byte[] data, int keyPurpose) throws Exception {
        CMSEnvelopedDataGenerator edGen = new CMSEnvelopedDataGenerator();
        CMSEnvelopedData ed;
        try {
            edGen.addKeyTransRecipient(cryptoToken.getPublicKey(getCAToken().getAliasFromPurpose(keyPurpose)), this.keyId);
            ed = edGen.generate(new CMSProcessableByteArray(data), CMSEnvelopedDataGenerator.AES256_CBC, "BC");
        } catch (Exception e) {
            log.error("-encryptKeys: ", e);
            throw new IOException(e.getMessage());
        }
        return ed.getEncoded();
    }

}
