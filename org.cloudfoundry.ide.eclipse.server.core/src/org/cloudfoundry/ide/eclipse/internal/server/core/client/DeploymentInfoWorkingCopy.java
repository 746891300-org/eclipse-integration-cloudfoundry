/*******************************************************************************
 * Copyright (c) 2013 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.core.client;

/**
 * A working copy of an application's {@link ApplicationDeploymentInfo}. If the
 * app module contains a deployment info, it will create a working copy of it.
 * If the app module does not contain a deployment info, it will create a
 * working copy with the app's default deployment values. The working copy is
 * intended to be short-lived, and not shared, and changes only take effect in
 * the cloud app module's deployment info if {@link #save()} is invoked.
 */
public abstract class DeploymentInfoWorkingCopy extends ApplicationDeploymentInfo {

	protected final CloudFoundryApplicationModule appModule;

	protected DeploymentInfoWorkingCopy(CloudFoundryApplicationModule appModule) {
		super(appModule.getDeployedApplicationName());
		this.appModule = appModule;

		if (appModule.getDeploymentInfo() != null) {
			setInfo(appModule.getDeploymentInfo());
		}
		else {
			setInfo(appModule.getDefaultDeploymentInfo());
		}
	}

	/**
	 * Saves the working copy in the associated
	 * {@link CloudFoundryApplicationModule}.
	 */
	abstract public void save();

}
