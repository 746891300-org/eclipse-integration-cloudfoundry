/*******************************************************************************
 * Copyright (c) 2012, 2013 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.core.spaces;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudSpace;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudErrorUtil;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryLoginHandler;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryPlugin;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryServer;
import org.cloudfoundry.ide.eclipse.internal.server.core.client.CloudFoundryServerBehaviour;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.osgi.util.NLS;
import org.springframework.web.client.RestClientException;

/**
 * Given a cloud server, if it supports cloud spaces, this handler will attempt
 * to resolve an actual cloud space if one is not already set in the cloud
 * server without going through cloud server behaviour. The reason it does not
 * go through the cloud server behaviour is that the cloud server may not yet
 * been fully validated, and therefore may not have a valid client. Such a case
 * will arise when part of a cloud space information like the org and space
 * names are set as properties in the server, but the full CloudSpace meta data
 * is missing as a client that requires a CloudSpace has not yet been created,
 * and therefore an actual look-up is required to obtain the CloudSpace based on
 * the org and space names saved as server properties.
 */
public class CloudSpaceServerLookup {

	private final CloudFoundryServer cloudServer;

	private CloudCredentials credentials;

	public CloudSpaceServerLookup(CloudFoundryServer cloudServer, CloudCredentials credentials) {
		this.cloudServer = cloudServer;
		this.credentials = credentials;
	}
	
	public CloudSpaceServerLookup(CloudFoundryServer cloudServer) {
		this(cloudServer, null);
	}

	protected CloudCredentials getCredentials() {
		if (credentials == null) {
			String userName = cloudServer.getUsername();
			String password = cloudServer.getPassword();
			credentials = new CloudCredentials(userName, password);
		}
		return credentials;
	}

	/**
	 * 
	 * @param monitor
	 * @return a cloud space descriptor, if a lookup was successful and matched
	 * the cloud space properties set in the server. Otherwise null is returned,
	 * including if a server does not support orgs and spaces.
	 * @throws CoreException
	 */
	public CloudFoundrySpace getCloudSpace(IProgressMonitor monitor) throws CoreException {
		CloudFoundrySpace cloudFoundrySpace = null;
		String url = cloudServer.getUrl();
		if (cloudServer.hasCloudSpace()) {
			cloudFoundrySpace = cloudServer.getCloudFoundrySpace();

			if (cloudFoundrySpace != null && cloudFoundrySpace.getSpace() == null) {
				// Do a look-up to determine the actual cloud space

				CloudOrgsAndSpaces actualSpaces = getCloudOrgsAndSpaces(monitor);

				if (actualSpaces != null) {
					CloudSpace cloudSpace = actualSpaces.getSpace(cloudFoundrySpace.getOrgName(),
							cloudFoundrySpace.getSpaceName());
					// Return null if no cloudspace was found.
					if (cloudSpace == null) {
						cloudFoundrySpace = null;
					}
					else {
						cloudFoundrySpace = new CloudFoundrySpace(cloudSpace);
					}
				}
			}
			if (cloudFoundrySpace == null) {
				throw new CoreException(CloudFoundryPlugin.getErrorStatus(NLS.bind(
						"Expected a cloud space for {0} but none were found.", new String[] { url })));
			}
		}

		return cloudFoundrySpace;
	}

	public CloudOrgsAndSpaces getCloudOrgsAndSpaces(IProgressMonitor monitor) throws CoreException {
		String url = cloudServer.getUrl();
		return getCloudOrgsAndSpaces(getCredentials(), url, monitor);
	}

	public static CloudOrgsAndSpaces getCloudOrgsAndSpaces(CloudCredentials credentials, String url,
			IProgressMonitor monitor) throws CoreException {
		CloudFoundryOperations operations = CloudFoundryServerBehaviour.createClient(url, credentials.getEmail(),
				credentials.getPassword());
		try {
			CloudOrgsAndSpaces orgsSpaces = null;
			CloudFoundryLoginHandler handler = new CloudFoundryLoginHandler(operations, url);
			handler.updateProxyInClient(operations);

			// Attempt to log in
			handler.login(monitor, 5, 5000);

			try {
				orgsSpaces = getCloudSpace(operations, monitor);

			}
			catch (CloudFoundryException cfe) {
				throw CloudErrorUtil.toCoreException(cfe);
			}
			catch (RestClientException e) {
				throw CloudErrorUtil.toCoreException(e);
			}

			return orgsSpaces;

		}
		catch (CoreException ce) {
			// Translate the cause to a user friendly message
			String validationMessage = CloudErrorUtil.getV2ValidationErrorMessage(ce);
			if (validationMessage != null) {
				ce = new CoreException(CloudFoundryPlugin.getErrorStatus(validationMessage));
			}
			throw ce;
		}

	}

	/**
	 * 
	 * @param credentials
	 * @param url server URL
	 * @param monitor
	 * @return list of all apps in all spaces for the given account.
	 * @throws CoreException if error occurred while retrieve list of apps.
	 */
	public List<CloudApplication> getAllOrgApps(IProgressMonitor monitor)
			throws CoreException {
		// By creating a client without a session cloud space, retrieving a list
		// of applications will
		// retrieve all the apps for all the spaces.
		String url = cloudServer.getUrl();
		CloudCredentials credentials = getCredentials();
		CloudFoundryOperations operations = CloudFoundryServerBehaviour.createClient(url, credentials.getEmail(),
				credentials.getPassword());
		try {
			List<CloudApplication> apps = null;
			CloudFoundryLoginHandler handler = new CloudFoundryLoginHandler(operations, url);
			handler.updateProxyInClient(operations);

			// Attempt to log in
			handler.login(monitor, 5, 5000);

			try {
				apps = operations.getApplications();
			}
			catch (CloudFoundryException cfe) {
				throw CloudErrorUtil.toCoreException(cfe);
			}
			catch (RestClientException e) {
				throw CloudErrorUtil.toCoreException(e);
			}

			return apps;

		}
		catch (CoreException ce) {
			// Translate the cause to a user friendly message
			String validationMessage = CloudErrorUtil.getV2ValidationErrorMessage(ce);
			if (validationMessage != null) {
				ce = new CoreException(CloudFoundryPlugin.getErrorStatus(validationMessage));
			}
			throw ce;
		}

	}

	private static CloudOrgsAndSpaces getCloudSpace(CloudFoundryOperations operations, IProgressMonitor monitor)
			throws CoreException {
		SubMonitor progress = SubMonitor.convert(monitor);
		progress.beginTask("Resolving list of Cloud Foundry organizations and spaces", IProgressMonitor.UNKNOWN);

		try {

			List<CloudSpace> foundSpaces = operations.getSpaces();
			if (foundSpaces != null && !foundSpaces.isEmpty()) {
				List<CloudSpace> actualSpaces = new ArrayList<CloudSpace>(foundSpaces);
				CloudOrgsAndSpaces orgsAndSpaces = new CloudOrgsAndSpaces(actualSpaces);
				return orgsAndSpaces;
			}

			return null;

		}
		catch (RuntimeException e) {
			if (e.getCause() instanceof IOException) {
				CloudFoundryPlugin
						.getDefault()
						.getLog()
						.log(new Status(IStatus.ERROR, CloudFoundryPlugin.PLUGIN_ID,
								"Parse error from server response", e.getCause()));
				throw new CoreException(new Status(IStatus.ERROR, CloudFoundryPlugin.PLUGIN_ID,
						"Unable to communicate with server"));
			}
			else {
				throw e;
			}
		}
		finally {
			progress.done();
		}
	}

}
