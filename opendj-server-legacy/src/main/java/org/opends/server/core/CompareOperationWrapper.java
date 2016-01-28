/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2016 ForgeRock AS
 */
package org.opends.server.core;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.DN;

/**
 * This abstract class wraps/decorates a given compare operation.
 * This class will be extended by sub-classes to enhance the
 * functionality of the CompareOperationBasis.
 */
public abstract class CompareOperationWrapper extends
    OperationWrapper<CompareOperation> implements CompareOperation
{

  /**
   * Creates a new compare operation based on the provided compare operation.
   *
   * @param compare The compare operation to wrap
   */
  public CompareOperationWrapper(CompareOperation compare)
  {
    super(compare);
  }

  @Override
  public ByteString getRawEntryDN()
  {
    return getOperation().getRawEntryDN();
  }

  @Override
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    getOperation().setRawEntryDN(rawEntryDN);
  }

  @Override
  public DN getEntryDN()
  {
    return getOperation().getEntryDN();
  }

  @Override
  public String getRawAttributeType()
  {
    return getOperation().getRawAttributeType();
  }

  @Override
  public void setRawAttributeType(String rawAttributeType)
  {
    getOperation().setRawAttributeType(rawAttributeType);
  }

  @Override
  public AttributeDescription getAttributeDescription()
  {
    return getOperation().getAttributeDescription();
  }

  @Override
  public ByteString getAssertionValue()
  {
    return getOperation().getAssertionValue();
  }

  @Override
  public void setAssertionValue(ByteString assertionValue)
  {
    getOperation().setAssertionValue(assertionValue);
  }
}
