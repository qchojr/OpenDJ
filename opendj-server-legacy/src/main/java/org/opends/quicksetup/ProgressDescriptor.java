/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */

package org.opends.quicksetup;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This class is used to describe the current state of the installation.
 * It contains the step in which the installation is, the current progress
 * ratio, the progress bar message and the details message (the logs).
 *
 * This class is used directly by the ProgressPanel to update its content
 * and has been designed to match the layout of that panel.  However as it
 * does not contain any dependency in terms of code with any Swing or UI package
 * component it has been decided to leave it on the installer package.
 *
 * In general the progress bar message and the details messages (log) are in
 * HTML form (but this class is independent of the format we use for the
 * messages).
 *
 */
public class ProgressDescriptor {

  private ProgressStep step;

  private Integer progressBarRatio;

  private LocalizableMessage progressBarMsg;

  private LocalizableMessage detailsMsg;

  /**
   * Constructor for the ProgressDescriptor.
   * @param step the current install step.
   * @param progressBarRatio the completed progress ratio (in percentage).
   * @param progressBarMsg the message to be displayed in the progress bar.
   * @param detailsMsg the logs.
   */
  public ProgressDescriptor(ProgressStep step,
      Integer progressBarRatio, LocalizableMessage progressBarMsg, LocalizableMessage detailsMsg)
  {
    this.step = step;
    this.progressBarRatio = progressBarRatio;
    this.progressBarMsg = progressBarMsg;
    this.detailsMsg = detailsMsg;
  }

  /**
   * Returns the details message (the log message) of the install.
   * @return the details message (the log message) of the install.
   */
  public LocalizableMessage getDetailsMsg()
  {
    return detailsMsg;
  }

  /**
   * Returns the progress bar message.
   * @return the progress bar message.
   */
  public LocalizableMessage getProgressBarMsg()
  {
    return progressBarMsg;
  }

  /**
   * Returns the progress bar ratio (the percentage of the install that is
   * completed).
   * @return the progress bar ratio (the percentage of the install that is
   * completed).
   */
  public Integer getProgressBarRatio()
  {
    return progressBarRatio;
  }

  /**
   * Returns the step of the install on which we are.
   * @return the step of the install on which we are.
   */
  public ProgressStep getProgressStep()
  {
    return step;
  }
}
