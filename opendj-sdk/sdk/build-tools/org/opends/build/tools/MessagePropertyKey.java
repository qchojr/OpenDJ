/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.build.tools;

/**
 * OpenDS messages defined in properties files must be defined with the
 * ordinal and in most cases category and severity encoded in the
 * message key. This class helps with generating and parsing of these
 * keys. Keys must be of the form CATEGORY_SEVERITY_DESCRIPTION_ORDINAL
 * where:
 * <ul>
 * <li>CATEGORY is the string representation of one of the
 * <code>Category</code> enums.</li>
 * <li>SEVERITY is the long or abbreviated form of one of the
 * <code>Severity</code> enums.</li>
 * <li>DESCRIPTION is an uppercase string containing characters and the
 * underscore character for describing the purpose of the message.</li>
 * <li>ORDINAL is an integer that makes the message unique witin the
 * property file.</li>
 * </ul>
 */
// TODO: move this class to GenerateMessageFile when DirectoryServer
// no longer needs to support dumpMessages()
public class MessagePropertyKey implements
    Comparable<MessagePropertyKey>
{

  private String description;

  private Integer ordinal;



  /**
   * Creates a message property key from a string value.
   *
   * @param keyString
   *          from properties file
   * @param includesOrdinal
   *          when true expects ordinals to be encoded in the keystring;
   *          when false the mandate is relaxed
   * @return MessagePropertyKey created from string
   */
  static public MessagePropertyKey parseString(String keyString,
      boolean includesOrdinal)
  {

    String description;
    Integer ordinal = null;

    String k = keyString;

    if (includesOrdinal)
    {
      int li = k.lastIndexOf("_");
      if (li != -1)
      {
        description = k.substring(0, li).toUpperCase();
      }
      else
      {
        throw new IllegalArgumentException("Incorrectly formatted key "
            + keyString);
      }

      try
      {
        String ordString = k.substring(li + 1);
        ordinal = Integer.parseInt(ordString);
      }
      catch (Exception nfe)
      {
        throw new IllegalArgumentException(
            "Error parsing ordinal for key " + keyString);
      }
    }
    else
    {
      description = k;
    }
    return new MessagePropertyKey(description, ordinal);
  }



  /**
   * Creates a parameterized instance.
   *
   * @param description
   *          of this key
   * @param ordinal
   *          of this key
   */
  public MessagePropertyKey(String description, Integer ordinal)
  {
    this.description = description;
    this.ordinal = ordinal;
  }



  /**
   * Gets the description of this key.
   *
   * @return description of this key
   */
  public String getDescription()
  {
    return this.description;
  }



  /**
   * Gets the ordinal of this key.
   *
   * @return ordinal of this key
   */
  public Integer getOrdinal()
  {
    return this.ordinal;
  }



  /**
   * Gets the name of the MessageDescriptor as it should appear in the
   * messages file.
   *
   * @return name of message descriptor
   */
  public String getMessageDescriptorName()
  {
    String name = this.description;

    name = name.replaceFirst("^MILD_WARN", "WARN");
    name = name.replaceFirst("^SEVERE_WARN", "WARN");
    name = name.replaceFirst("^MILD_ERR", "ERR");
    name = name.replaceFirst("^SEVERE_ERR", "ERR");
    name = name.replaceFirst("^FATAL_ERR", "ERR");

    return name;
  }



  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return getPropertyKeyName(true);
  }



  /**
   * Gets the name of the key as it would appear in a properties file.
   *
   * @param includeOrdinal
   *          in the name
   * @return string representing the property key
   */
  public String getPropertyKeyName(boolean includeOrdinal)
  {
    StringBuilder sb = new StringBuilder();
    sb.append(description);
    if (ordinal != null && includeOrdinal)
    {
      sb.append("_");
      sb.append(ordinal);
    }
    return sb.toString();
  }



  /**
   * {@inheritDoc}
   */
  public int compareTo(MessagePropertyKey k)
  {
    if (ordinal == k.ordinal)
    {
      return description.compareTo(k.description);
    }
    else
    {
      return ordinal.compareTo(k.ordinal);
    }
  }

}
