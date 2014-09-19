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

package org.cesecore.authorization.user;

import java.security.InvalidParameterException;

import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.cesecore.authorization.user.matchvalues.AccessMatchValue;
import org.cesecore.authorization.user.matchvalues.AccessMatchValueReverseLookupRegistry;
import org.cesecore.dbprotection.ProtectedData;
import org.cesecore.dbprotection.ProtectionStringBuilder;

/**
 * Represents an aspect of an external user. It can be set to match one administrator's <i>DN</i> or an entire organization by matching against
 * <i>O</i>.
 * 
 * @version $Id: AccessUserAspectData.java 17819 2013-10-15 07:02:08Z samuellb $
 */
@Entity
@Table(name = "AccessUserAspectData")
public class AccessUserAspectData extends ProtectedData implements AccessUserAspect, Comparable<AccessUserAspectData> {

    private static final long serialVersionUID = 2504191317243484124L;
    private int primaryKey;
    private String tokenType;
    private Integer caId;
    private int rowVersion = 0;
    private String rowProtection;
    private Integer matchWith;
    private AccessMatchType matchType;
    private String matchValue;

    public AccessUserAspectData(final String roleName, final int caId, final AccessMatchValue matchWith, final AccessMatchType matchType,
            final String matchValue) {
        if (roleName == null) {
            throw new InvalidParameterException("Attempted to create an AccessUserAspectData with roleName == null");
        } else {
            this.primaryKey = generatePrimaryKey(roleName, caId, matchWith, matchType, matchValue);
        }
        if (matchWith == null) {
            throw new InvalidParameterException("Attempted to create an AccessUserAspectData with matchWith == null");
        } else {
            this.matchWith = matchWith.getNumericValue();
        }
        if (matchType == null) {
            throw new InvalidParameterException("Attempted to create an AccessUserAspectData with matchType == null");
        } else {
            this.matchType = matchType;
        }
        if (matchValue == null) {
            throw new InvalidParameterException("Attempted to create an AccessUserAspectData with matchValue == null");
        } else {
            this.matchValue = matchValue;
        }
        this.caId = caId;
        this.tokenType = matchWith.getTokenType();
    }

    /**
     * Private to stop default instantiation.
     */
    @SuppressWarnings("unused")
    private AccessUserAspectData() {

    }

    //@Id @Column
    public int getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(int primaryKey) {
        this.primaryKey = primaryKey;
    }

    @Override
    public int getMatchWith() {
        return matchWith;
    }

    @Override
    public void setMatchWith(Integer matchWith) {
        if (matchWith == null) {
            throw new InvalidParameterException("Invalid to set matchWith == null");
        }
        this.matchWith = matchWith;
    }

    @Override
    public int getMatchType() {
        if (matchType == null) {
            return AccessMatchType.TYPE_NONE.getNumericValue();
        }
        return matchType.getNumericValue();
    }

    @Override
    public void setMatchType(Integer matchType) {
        if (matchType == null) {
            throw new InvalidParameterException("Invalid to set matchType == null");
        }
        this.matchType = AccessMatchType.matchFromDatabase(matchType);
    }

    @Override
    public void setMatchTypeAsValue(AccessMatchType matchType) {
        if (matchType == null) {
            throw new InvalidParameterException("Invalid to set matchType == null");
        }
        this.matchType = matchType;
    }

    @Override
    @Transient
    public AccessMatchType getMatchTypeAsType() {
        return matchType;
    }

    @Override
    public String getMatchValue() {
        return matchValue;
    }

    @Override
    public void setMatchValue(String matchValue) {
        if (matchValue == null) {
            throw new InvalidParameterException("Invalid to set matchValue == null");
        }
        this.matchValue = matchValue;
    }

    @Override
    public Integer getCaId() {
        return caId;
    }

    @Override
    public void setCaId(Integer caId) {
        if (caId == null) {
            throw new InvalidParameterException("Invalid to set caId == null");
        }
        this.caId = caId;
    }

    public int getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(final int rowVersion) {
        this.rowVersion = rowVersion;
    }

    public String getRowProtection() {
        return rowProtection;
    }

    public void setRowProtection(final String rowProtection) {
        this.rowProtection = rowProtection;
    }

    @Override
    public String getTokenType() {
        return tokenType;
    }

    @Override
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public static int generatePrimaryKey(final String roleName, final int caId, final AccessMatchValue matchWith, final AccessMatchType matchType,
            final String matchValue) {
        return generatePrimaryKey(roleName, caId, matchWith.getNumericValue(), matchType, matchValue);
    }
    
    public static int generatePrimaryKey(final String roleName, final int caId, final int matchWith, final AccessMatchType matchType,
            final String matchValue) {
        final int roleNameHash = roleName == null ? 0 : roleName.hashCode();
        final int matchValueHash = matchValue == null ? 0 : matchValue.hashCode();
        return (roleNameHash & matchValueHash) ^ caId ^ matchWith ^ matchType.getNumericValue();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((caId == null) ? 0 : caId.hashCode());
        result = prime * result + ((matchType == null) ? 0 : matchType.hashCode());
        result = prime * result + ((matchValue == null) ? 0 : matchValue.hashCode());
        result = prime * result + ((matchWith == null) ? 0 : matchWith.hashCode());
        result = prime * result + primaryKey;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AccessUserAspectData other = (AccessUserAspectData) obj;
        if (caId == null) {
            if (other.caId != null) {
                return false;
            }
        } else if (!caId.equals(other.caId)) {
            return false;
        }
        if (matchType != other.matchType) {
            return false;
        }
        if (matchValue == null) {
            if (other.matchValue != null) {
                return false;
            }
        } else if (!matchValue.equals(other.matchValue)) {
            return false;
        }
        if (matchWith.intValue() != other.matchWith.intValue()) {
            return false;
        }
        if (primaryKey != other.primaryKey) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return AccessMatchValueReverseLookupRegistry.INSTANCE.performReverseLookup(tokenType, matchWith).name() + " matching '" + matchValue
                + "' as " + matchType.name();
    }

    //
    // Start Database integrity protection methods
    //

    @Transient
    @Override
    protected String getProtectString(final int version) {
        final ProtectionStringBuilder build = new ProtectionStringBuilder();
        // What is important to protect here is the data that we define
        // rowVersion is automatically updated by JPA, so it's not important, it is only used for optimistic locking
        build.append(getPrimaryKey()).append(getMatchWith()).append(getMatchType()).append(getMatchValue()).append(getCaId());
        return build.toString();
    }

    @Transient
    @Override
    protected int getProtectVersion() {
        return 1;
    }

    @PrePersist
    @PreUpdate
    @Override
    protected void protectData() {
        super.protectData();
    }

    @PostLoad
    @Override
    protected void verifyData() {
        super.verifyData();
    }

    @Override
    @Transient
    protected String getRowId() {
        return String.valueOf(getPrimaryKey());
    }

    //
    // End Database integrity protection methods
    //

    @Override
    public int compareTo(AccessUserAspectData o) {
        return new CompareToBuilder().append(this.matchValue, o.matchValue).toComparison();
    }
}
