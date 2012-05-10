/*******************************************************************************
 * Copyright (c) 2012 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.ui.actions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.cloudfoundry.ide.eclipse.internal.server.core.CaldecottTunnelDescriptor;
import org.cloudfoundry.ide.eclipse.internal.server.core.CaldecottTunnelHandler;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryPlugin;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryServer;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryServerBehaviour;
import org.cloudfoundry.ide.eclipse.internal.server.ui.CloudFoundryImages;
import org.cloudfoundry.ide.eclipse.internal.server.ui.editor.CaldecottEditorActionAdapter;
import org.cloudfoundry.ide.eclipse.internal.server.ui.editor.CloudFoundryApplicationsEditorPage;
import org.cloudfoundry.ide.eclipse.internal.server.ui.editor.StartAndAddCaldecottService;
import org.cloudfoundry.ide.eclipse.internal.server.ui.wizards.CaldecottTunnelWizard;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;

public class CaldecottUIHelper {

	private final CloudFoundryServer cloudServer;

	public CaldecottUIHelper(CloudFoundryServer cloudServer) {
		this.cloudServer = cloudServer;
	}

	public void displayCaldecottTunnelConnections() {
		final CloudFoundryServerBehaviour behaviour = cloudServer.getBehaviour();
		UIJob uiJob = new UIJob("Show Caldecott Tunnels") {

			public IStatus runInUIThread(IProgressMonitor monitor) {
				Shell shell = PlatformUI.getWorkbench().getModalDialogShellProvider().getShell();

				CaldecottTunnelWizard wizard = new CaldecottTunnelWizard(behaviour);
				WizardDialog dialog = new WizardDialog(shell, wizard);
				if (dialog.open() == Window.OK) {

					Set<CaldecottTunnelDescriptor> descriptorsToRemove = wizard.getDescriptorsToRemove();
					if (descriptorsToRemove != null) {
						for (CaldecottTunnelDescriptor descriptor : descriptorsToRemove) {
							try {
								new CaldecottTunnelHandler(cloudServer).stopAndDeleteCaldecottTunnel(
										descriptor.getServiceName(), monitor);
							}
							catch (CoreException e) {
								CloudFoundryPlugin.logError(e);
							}
						}
					}
				}
				return Status.OK_STATUS;
			}

		};
		uiJob.schedule();
	}

	/**
	 * Returns a list of applicable Caldecott Actions given the selection, or
	 * empty list if not actions are applicable.
	 * @param selection
	 * @param editorPage
	 * @return non-null list of actions. May be empty.
	 */
	public List<IAction> getCaldecottActions(IStructuredSelection selection,
			final CloudFoundryApplicationsEditorPage editorPage) {
		Collection<String> selectedServices = StartAndAddCaldecottService.getServiceNames(selection);
		List<IAction> actions = new ArrayList<IAction>();
		if (selectedServices != null && !selectedServices.isEmpty()) {
			final List<String> servicesToAdd = new ArrayList<String>(selectedServices);
			Action addCaldecottTunnel = new Action("Start Caldecott tunnel", CloudFoundryImages.CONNECT) {
				public void run() {

					Job job = new Job("Starting Caldecott tunnel") {

						@Override
						protected IStatus run(IProgressMonitor monitor) {
							new CaldecottEditorActionAdapter(cloudServer.getBehaviour(), editorPage)
									.addServiceAndCreateTunnel(servicesToAdd, monitor);
							return Status.OK_STATUS;
						}
					};
					job.setSystem(false);
					job.schedule();

				}
			};
			actions.add(addCaldecottTunnel);

			if (new CaldecottTunnelHandler(cloudServer).hasCaldecottTunnels()) {
				actions.add(new CaldecottTunnelAction(cloudServer));
			}
		}
		return actions;
	}

}