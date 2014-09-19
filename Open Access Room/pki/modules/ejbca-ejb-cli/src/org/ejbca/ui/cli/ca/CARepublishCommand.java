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

package org.ejbca.ui.cli.ca;

import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.certificate.CertificateInfo;
import org.cesecore.certificates.certificate.CertificateStoreSessionRemote;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionRemote;
import org.cesecore.certificates.crl.CrlStoreSessionRemote;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.util.cert.CrlExtensions;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.core.ejb.ca.publisher.PublisherSessionRemote;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionRemote;
import org.ejbca.ui.cli.CliUsernameException;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.util.CliTools;

/**
 * Re-publishes the certificates of all users belonging to a particular CA.
 * 
 * @version $Id: CARepublishCommand.java 17764 2013-10-09 13:01:40Z mikekushner $
 */
public class CARepublishCommand extends BaseCaAdminCommand {

    @Override
    public String getSubCommand() {
        return "republish";
    }
    @Override
    public String getDescription() {
        return "Re-publishes the certificates of all users belonging to a particular CA";
    }
    @Override
    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            args = parseUsernameAndPasswordFromArgs(args);
        } catch (CliUsernameException e) {
            return;
        }
        
        try {
            // Get and remove switches
            List<String> argsList = CliTools.getAsModifyableList(args);
            boolean addAll = argsList.remove("-all");
            args = argsList.toArray(new String[argsList.size()]);
            // Parse the rest of the arguments
            if (args.length < 2) {
                getLogger().info("Description: " + getDescription());
                getLogger().info("Usage: " + getCommand() + " <CA name> [-all]");
                getLogger().info(" -all   republish all certificates for each user instead of just the latest");
                return;
            }
            String caname = args[1];
            CryptoProviderTools.installBCProvider();
            // Get the CAs info and id
            CAInfo cainfo = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getCAInfo(getAuthenticationToken(cliUserName, cliPassword), caname);
            if (cainfo == null) {
                getLogger().info("CA with name '" + caname + "' does not exist.");
                return;
            }
            // Publish the CAs certificate and CRL
            Collection<Certificate> cachain = cainfo.getCertificateChain();
            Iterator<Certificate> caiter = cachain.iterator();
            if (caiter.hasNext()) {
                final X509Certificate cacert = (X509Certificate) caiter.next();
                final byte[] crlbytes = ejb.getRemoteSession(CrlStoreSessionRemote.class).getLastCRL(cainfo.getSubjectDN(), false);
                // Get the CRLnumber
                X509CRL crl = CertTools.getCRLfromByteArray(crlbytes);
                int crlNumber = CrlExtensions.getCrlNumber(crl).intValue();
                final Collection<Integer> capublishers = cainfo.getCRLPublishers();
                // Store cert and CRL in ca publishers.
                if (capublishers != null) {
                    String fingerprint = CertTools.getFingerprintAsString(cacert);
                    String username = ejb.getRemoteSession(CertificateStoreSessionRemote.class).findUsernameByCertSerno(cacert.getSerialNumber(), cacert.getIssuerDN().getName());
                    CertificateInfo certinfo = ejb.getRemoteSession(CertificateStoreSessionRemote.class).getCertificateInfo(fingerprint);
                    ejb.getRemoteSession(PublisherSessionRemote.class).storeCertificate(getAuthenticationToken(cliUserName, cliPassword), capublishers, cacert, username, null, cainfo.getSubjectDN(), fingerprint, certinfo
                            .getStatus(), certinfo.getType(), certinfo.getRevocationDate().getTime(), certinfo.getRevocationReason(), certinfo.getTag(),
                            certinfo.getCertificateProfileId(), certinfo.getUpdateTime().getTime(), null);
                    getLogger().info("Certificate published for " + caname);
                    if ( crlbytes!=null && crlbytes.length>0 && crlNumber>0 ) {
                        ejb.getRemoteSession(PublisherSessionRemote.class).storeCRL(getAuthenticationToken(cliUserName, cliPassword), capublishers, crlbytes, fingerprint, crlNumber, cainfo.getSubjectDN());
                        getLogger().info("CRL with number "+crlNumber+" published for " + caname);
                    } else {
                        getLogger().info("CRL not published, no CRL createed for CA?");
                    }
                } else {
                    getLogger().info("No publishers configured for the CA, no CA certificate or CRL published.");
                }
            } else {
                getLogger().info("CA does not have a certificate, no certificate or CRL published!");
            }

            // Get all users for this CA
            Collection<EndEntityInformation> coll = ejb.getRemoteSession(EndEntityManagementSessionRemote.class).findAllUsersByCaId(getAuthenticationToken(cliUserName, cliPassword), cainfo.getCAId());
            Iterator<EndEntityInformation> iter = coll.iterator();
            while (iter.hasNext()) {
            	EndEntityInformation data = iter.next();
                getLogger().info(
                        "User: " + data.getUsername() + ", \"" + data.getDN() + "\", \"" + data.getSubjectAltName() + "\", " + data.getEmail() + ", "
                                + data.getStatus() + ", " + data.getType().getHexValue() + ", " + data.getTokenType() + ", " + data.getHardTokenIssuerId() + ", "
                                + data.getCertificateProfileId());

                if (data.getCertificateProfileId() > 0) { // only if we find a
                    // certificate profile
                    CertificateProfile certProfile = ejb.getRemoteSession(CertificateProfileSessionRemote.class).getCertificateProfile(data.getCertificateProfileId());
                    if (certProfile == null) {
                        getLogger().error("Can not get certificate profile with id: " + data.getCertificateProfileId());
                        continue;
                    }
                    // Get an ordered list of certificates, last expire date first
                    List<Certificate> certCol = ejb.getRemoteSession(CertificateStoreSessionRemote.class).findCertificatesByUsername(data.getUsername());
                    if ((certCol != null) && certCol.iterator().hasNext()) {
                            if (certProfile.getPublisherList() != null) {
                                getLogger().info("Re-publishing user " + data.getUsername());
                                if (addAll) {
                                    getLogger().info("Re-publishing all certificates (" + certCol.size() + ").");
                                    // Reverse the collection so we publish the latest certificate last
                                    Collections.reverse(certCol); // now the latest (last expire date) certificate is last in the List
                                    Iterator<Certificate> i = certCol.iterator();
                                    while (i.hasNext()) {
                                        X509Certificate c = (X509Certificate) i.next();
                                        publishCert(getAuthenticationToken(cliUserName, cliPassword), data, certProfile, c);
                                    }
                                } else {
                                    // Only publish the latest one (last expire date)
                                    // The latest one is the first in the List according to findCertificatesByUsername()
                                    publishCert(getAuthenticationToken(cliUserName, cliPassword), data, certProfile, (X509Certificate)certCol.iterator().next());
                                }
                            } else {
                                getLogger().info("Not publishing user " + data.getUsername() + ", no publisher in certificate profile.");
                            }
                    } else {
                        getLogger().info("No certificate to publish for user " + data.getUsername());
                    }
                } else {
                    getLogger().info("No certificate profile id exists for user " + data.getUsername());
                }
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
    }

    private void publishCert(AuthenticationToken authenticationToken, EndEntityInformation data, CertificateProfile certProfile, X509Certificate cert) {
        try {
            String fingerprint = CertTools.getFingerprintAsString(cert);
            CertificateInfo certinfo = ejb.getRemoteSession(CertificateStoreSessionRemote.class).getCertificateInfo(fingerprint);
            final String userDataDN = data.getCertificateDN();
            boolean ret = ejb.getRemoteSession(PublisherSessionRemote.class).storeCertificate(authenticationToken, certProfile.getPublisherList(), cert, data.getUsername(), data.getPassword(), userDataDN,
                    fingerprint, certinfo.getStatus(), certinfo.getType(), certinfo.getRevocationDate().getTime(), certinfo.getRevocationReason(), certinfo
                            .getTag(), certinfo.getCertificateProfileId(), certinfo.getUpdateTime().getTime(), null);
            if (!ret) {
                getLogger().error("Failed to publish certificate for user " + data.getUsername() + ", continuing with next user. Publish returned false.");
            }
        } catch (Exception e) {
            // catch failure to publish one user and continue with the rest
            getLogger().error("Failed to publish certificate for user " + data.getUsername() + ", continuing with next user. "+e.getMessage());
        }
    }
}
