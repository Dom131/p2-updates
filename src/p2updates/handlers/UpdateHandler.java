package p2updates.handlers;

import java.net.URI;
import java.net.URISyntaxException;

import javax.inject.Inject;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.e4.ui.workbench.IWorkbench;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.operations.ProvisioningJob;
import org.eclipse.equinox.p2.operations.ProvisioningSession;
import org.eclipse.equinox.p2.operations.UpdateOperation;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateHandler {
	Logger log = LoggerFactory.getLogger(this.getClass());
	private static final String REPOSITORY_LOC = System.getProperty("UpdateHandler.Repo",
			"file:///C:/Users/Dom/Documents/repository");
	@Inject
	UISynchronize ui;

	@Execute
	public void execute(final IProvisioningAgent agent, final Shell shell, final UISynchronize sync,
			final IWorkbench workbench) {
		log.info("UpdateHandler execute >>");
		Job updateJob = new Job("Update Job") {
			@Override
			protected IStatus run(final IProgressMonitor monitor) {
				return checkForUpdates(agent, shell, sync, workbench, monitor);
			}
		};
		updateJob.schedule();
	}

	private IStatus checkForUpdates(final IProvisioningAgent agent, final Shell shell, final UISynchronize sync,
			final IWorkbench workbench, IProgressMonitor monitor) {
		log.info("checkForUpdates execute >>");
		// configure update operation
		final ProvisioningSession session = new ProvisioningSession(agent);
		final UpdateOperation operation = new UpdateOperation(session);
		configureUpdate(operation);
		log.info("configureUpdate execute >>");
		// check for updates, this causes I/O
		IStatus status;
		try {
			status = operation.resolveModal(monitor);
			if (status.getCode() == UpdateOperation.STATUS_NOTHING_TO_UPDATE) {
				log.info("failed to find updates (inform user and exit) >>");
				showMessage(shell, sync);
				return Status.CANCEL_STATUS;
			}
		} catch (Exception e) {
			log.error("", e);
		}
		log.info("check for updates, this causes I/O >>");
		// failed to find updates (inform user and exit)

		// run installation
		final ProvisioningJob provisioningJob = operation.getProvisioningJob(monitor);
		log.info("run installation >>");
		// updates cannot run from within Eclipse IDE!!!
		if (provisioningJob == null) {
			log.error("Trying to update from the Eclipse IDE? This won't work!");
			ui.asyncExec(new Runnable() {
				@Override
				public void run() {
					MessageDialog.openQuestion(shell, "Error",
							"Trying to update from the Eclipse IDE? This won't work!");
				}
			});
			return Status.CANCEL_STATUS;
		}
		try {
			configureProvisioningJob(provisioningJob, shell, sync, workbench);
		} catch (Exception e) {
			log.error("", e);
		}
		log.info("provisioningJob  schedule>>");
		provisioningJob.schedule();
		return Status.OK_STATUS;

	}

	private void configureProvisioningJob(ProvisioningJob provisioningJob, final Shell shell, final UISynchronize sync,
			final IWorkbench workbench) {
		log.info("configureProvisioningJob>>");
		// register a job change listener to track
		// installation progress and notify user upon success
		provisioningJob.addJobChangeListener(new JobChangeAdapter() {
			
			@Override
			public void done(IJobChangeEvent event) {
				log.info("IJobChangeEvent>>" +  event.getResult().getMessage());
				log.error("exception",event.getResult().getException());
				if (event.getResult().isOK()) {
					sync.syncExec(new Runnable() {
						@Override
						public void run() {
							boolean restart = MessageDialog.openQuestion(shell, "Updates installed, restart?",
									"Updates have been installed. Do you want to restart?");
							if (restart) {
								workbench.restart();
							}
						}
					});
				}
				super.done(event);
			}
		});

	}

	private void showMessage(final Shell parent, final UISynchronize sync) {
		sync.syncExec(new Runnable() {
			@Override
			public void run() {
				MessageDialog.openWarning(parent, "No update",
						"No updates for the current installation have been found.");
			}
		});
	}

	private UpdateOperation configureUpdate(final UpdateOperation operation) {
		// create uri and check for validity
		URI uri = null;
		try {
			log.info(REPOSITORY_LOC);
			uri = new URI("http://localhost:8080/repository");
			// uri = new URI(REPOSITORY_LOC);
		} catch (final URISyntaxException e) {
			log.error("configureUpdate>>", e);
			return null;
		}

		// set location of artifact and metadata repo
		operation.getProvisioningContext().setArtifactRepositories(new URI[] { uri });
		operation.getProvisioningContext().setMetadataRepositories(new URI[] { uri });
		return operation;
	}

}