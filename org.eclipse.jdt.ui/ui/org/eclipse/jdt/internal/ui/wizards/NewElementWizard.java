/*
 * Licensed Materials - Property of IBM,
 * WebSphere Studio Workbench
 * (c) Copyright IBM Corp 1999, 2000
 */
package org.eclipse.jdt.internal.ui.wizards;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.Wizard;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.WorkspaceModifyDelegatingOperation;
import org.eclipse.ui.part.ISetSelectionTarget;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public abstract class NewElementWizard extends Wizard implements INewWizard {

	private static final String PREFIX_OP_ERROR= "NewElementWizard.op_error.";

	public NewElementWizard() {
		setNeedsProgressMonitor(true);
	}
	
	protected IStructuredSelection getSelection() {
		IWorkbenchWindow window= JavaPlugin.getDefault().getActiveWorkbenchWindow();
		if (window != null) {
			ISelection sel= window.getSelectionService().getSelection();
			if (sel instanceof IStructuredSelection) {
				return (IStructuredSelection)sel;
			}
		}
		return null; 
	}
	

	protected void revealSelection(final Object toSelect) {
		IWorkbenchPage activePage= JavaPlugin.getDefault().getActivePage();
		if (activePage != null) {
			final IWorkbenchPart focusPart= activePage.getActivePart();
			if (focusPart instanceof ISetSelectionTarget) {
				Display d= getShell().getDisplay();
				d.asyncExec(new Runnable() {
					public void run() {
						ISelection selection= new StructuredSelection(toSelect);
						((ISetSelectionTarget)focusPart).selectReveal(selection);
					}
				});
			}
		}
	}

	protected void openResource(final IResource resource) {
		if (resource.getType() == IResource.FILE) {
			final IWorkbenchPage activePage= JavaPlugin.getDefault().getActivePage();
			if (activePage != null) {
				final Display display= getShell().getDisplay();
				if (display != null) {
					display.asyncExec(new Runnable() {
						public void run() {
							try {
								activePage.openEditor((IFile)resource);
							} catch (PartInitException e) {
								MessageDialog.openError(getShell(), "Error", e.getMessage());
							}
						}
					});
				}
			}
		}
	}
	
	/**
	 * Called by the wizard to create the new element
	 */		
	public boolean finishPage(NewElementWizardPage page) {
		IRunnableWithProgress runnable= page.getRunnable();
		if (runnable != null) {
			return invokeRunnable(runnable);
		}
		return true;
	}
		
	
	/**
	 * Utility method: call a runnable in a WorkbenchModifyDelegatingOperation
	 */
	protected boolean invokeRunnable(IRunnableWithProgress runnable) {
		IRunnableWithProgress op= new WorkspaceModifyDelegatingOperation(runnable);
		try {
			getContainer().run(false, true, op);
		} catch (InvocationTargetException e) {
			Shell shell= getShell();
			if (!ExceptionHandler.handle(e.getTargetException(), shell, JavaPlugin.getResourceBundle(), PREFIX_OP_ERROR)) {
				MessageDialog.openError(shell, "Error", e.getTargetException().getMessage());
			}
			return false;
		} catch  (InterruptedException e) {
			return false;
		}
		return true;
	}	
	
	/**
	 * @see INewWizard#init
	 */
	public void init(IWorkbench workbench, IStructuredSelection selection) {	
	}
	
}