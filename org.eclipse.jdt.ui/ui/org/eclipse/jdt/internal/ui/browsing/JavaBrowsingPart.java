/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.browsing;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.IWorkingCopy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.CompositeActionGroup;
import org.eclipse.jdt.internal.ui.dnd.DelegatingDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.DelegatingDropAdapter;
import org.eclipse.jdt.internal.ui.dnd.LocalSelectionTransfer;
import org.eclipse.jdt.internal.ui.dnd.ResourceTransferDragAdapter;
import org.eclipse.jdt.internal.ui.dnd.TransferDragSourceListener;
import org.eclipse.jdt.internal.ui.dnd.TransferDropTargetListener;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.internal.ui.javaeditor.JarEntryEditorInput;
import org.eclipse.jdt.internal.ui.packageview.PackagesMessages;
import org.eclipse.jdt.internal.ui.packageview.SelectionTransferDragAdapter;
import org.eclipse.jdt.internal.ui.packageview.SelectionTransferDropAdapter;
import org.eclipse.jdt.internal.ui.preferences.JavaBasePreferencePage;
import org.eclipse.jdt.internal.ui.util.JavaUIHelp;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabels;
import org.eclipse.jdt.internal.ui.viewsupport.ProblemTableViewer;
import org.eclipse.jdt.internal.ui.viewsupport.StatusBarUpdater;
import org.eclipse.jdt.internal.ui.workingsets.WorkingSetFilterActionGroup;
import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jdt.ui.JavaElementSorter;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;
import org.eclipse.jdt.ui.actions.BuildActionGroup;
import org.eclipse.jdt.ui.actions.CCPActionGroup;
import org.eclipse.jdt.ui.actions.CustomFiltersActionGroup;
import org.eclipse.jdt.ui.actions.GenerateActionGroup;
import org.eclipse.jdt.ui.actions.ImportActionGroup;
import org.eclipse.jdt.ui.actions.JavaSearchActionGroup;
import org.eclipse.jdt.ui.actions.OpenEditorActionGroup;
import org.eclipse.jdt.ui.actions.OpenViewActionGroup;
import org.eclipse.jdt.ui.actions.RefactorActionGroup;
import org.eclipse.jdt.ui.actions.ShowActionGroup;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.actions.NewWizardMenu;
import org.eclipse.ui.part.ResourceTransfer;
import org.eclipse.ui.part.ViewPart;

import org.eclipse.search.ui.ISearchResultView;


abstract class JavaBrowsingPart extends ViewPart implements IMenuListener, ISelectionListener {

	private ILabelProvider fLabelProvider;
	private ILabelProvider fTitleProvider;
	private StructuredViewer fViewer;
	private IMemento fMemento;
	private JavaElementTypeComparator fTypeComparator;
	
	// Actions
	private WorkingSetFilterActionGroup fWorkingSetFilterActionGroup;
	private boolean fHasWorkingSetFilter= true;
	private boolean fHasCustomFilter= true;
	private OpenEditorActionGroup fOpenEditorGroup;
	private CCPActionGroup fCCPActionGroup;
	private BuildActionGroup fBuildActionGroup;
	protected CompositeActionGroup fActionGroups;

	// Filters
	private CustomFiltersActionGroup fCustomFiltersActionGroup;
	
	private Menu fContextMenu;		
	private IWorkbenchPart fPreviousSelectionProvider;
	private Object fPreviousSelectedElement;
			
	/*
	 * Ensure selection changed events being processed only if
	 * initiated by user interaction with this part.
	 */
	private boolean fProcessSelectionEvents= true;

	private IPartListener fPartListener= new IPartListener() {
		public void partActivated(IWorkbenchPart part) {
			setSelectionFromEditor(part);
		}
		public void partBroughtToTop(IWorkbenchPart part) {
		}
		public void partClosed(IWorkbenchPart part) {
		}
		public void partDeactivated(IWorkbenchPart part) {
		}
		public void partOpened(IWorkbenchPart part) {
		}
	};

	/*
	 * Implements method from IViewPart.
	 */
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		fMemento= memento;
	}

	/*
	 * Implements method from IViewPart.
	 */
	public void saveState(IMemento memento) {
		if (fViewer == null) {
			// part has not been created
			if (fMemento != null) //Keep the old state;
				memento.putMemento(fMemento);
			return;
		}
		if (fHasWorkingSetFilter)
			fWorkingSetFilterActionGroup.saveState(memento);
		if (fHasCustomFilter)
			fCustomFiltersActionGroup.saveState(memento);
	}	

	protected void restoreState(IMemento memento) {
		if (fHasWorkingSetFilter)
			fWorkingSetFilterActionGroup.restoreState(memento);
		if (fHasCustomFilter)
			fCustomFiltersActionGroup.restoreState(memento);
	}	

	/**
	 * Creates the search list inner viewer.
	 */
	public void createPartControl(Composite parent) {
		Assert.isTrue(fViewer == null);

		fTypeComparator= new JavaElementTypeComparator();

		// Setup viewer
		fViewer= createViewer(parent);

		fLabelProvider= createLabelProvider();
		ILabelDecorator decorationMgr= PlatformUI.getWorkbench().getDecoratorManager().getLabelDecorator();
		fViewer.setLabelProvider(new DecoratingLabelProvider(fLabelProvider, decorationMgr));
		
		fViewer.setSorter(new JavaElementSorter());
		fViewer.setUseHashlookup(true);
		fTitleProvider= createTitleProvider();
		
		MenuManager menuMgr= new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(this);
		fContextMenu= menuMgr.createContextMenu(fViewer.getControl());
		fViewer.getControl().setMenu(fContextMenu);
		getSite().registerContextMenu(menuMgr, fViewer);
		getSite().setSelectionProvider(fViewer);

		createActions(); // call before registering for selection changes
		addKeyListener();

		// Custom filter group
		if (fHasCustomFilter)
			fCustomFiltersActionGroup= new CustomFiltersActionGroup(this, fViewer);

		if (fMemento != null)
			restoreState(fMemento);
		fMemento= null;

		getSite().setSelectionProvider(fViewer);
		
		// Status line
		IStatusLineManager slManager= getViewSite().getActionBars().getStatusLineManager();
		fViewer.addSelectionChangedListener(new StatusBarUpdater(slManager));
	
		
		hookViewerListeners();

		// Filters
		addFilters();

		// Initialize viewer input
		fViewer.setContentProvider(createContentProvider());
		setInitialInput();
		
		initDragAndDrop();
		
		// Initialize selecton
		setInitialSelection();
		
		// Listen to workbench window changes
		getViewSite().getWorkbenchWindow().getSelectionService().addPostSelectionListener(this);
		getViewSite().getPage().addPartListener(fPartListener);
		
		fillActionBars();
		
		setHelp();
	}

	private void initDragAndDrop() {
		int ops= DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_LINK;
		Transfer[] transfers= new Transfer[] {
			LocalSelectionTransfer.getInstance(), 
			ResourceTransfer.getInstance()};
		
		// Drop Adapter
		TransferDropTargetListener[] dropListeners= new TransferDropTargetListener[] {
			new SelectionTransferDropAdapter(fViewer)
		};
		fViewer.addDropSupport(ops | DND.DROP_DEFAULT, transfers, new DelegatingDropAdapter(dropListeners));
		
		// Drag Adapter
		Control control= fViewer.getControl();
		TransferDragSourceListener[] dragListeners= new TransferDragSourceListener[] {
			new SelectionTransferDragAdapter(fViewer),
			new ResourceTransferDragAdapter(fViewer)
		};
		DragSource source= new DragSource(control, ops);
		// Note, that the transfer agents are set by the delegating drag adapter itself.
		source.addDragListener(new DelegatingDragAdapter(dragListeners) {
			public void dragStart(DragSourceEvent event) {
				IStructuredSelection selection= (IStructuredSelection)getSelectionProvider().getSelection();
				for (Iterator iter= selection.iterator(); iter.hasNext(); ) {
					if (iter.next() instanceof IMember) {
						setPossibleListeners(new TransferDragSourceListener[] {new SelectionTransferDragAdapter(fViewer)});
						break;
					}
				}
				super.dragStart(event);
			}
		});
	}
	
	protected void fillActionBars() {
		IActionBars actionBars= getViewSite().getActionBars();
		IToolBarManager toolBar= actionBars.getToolBarManager();
		fillToolBar(toolBar);
		if (fHasWorkingSetFilter)
			fWorkingSetFilterActionGroup.fillActionBars(getViewSite().getActionBars());		

		actionBars.updateActionBars();
	
		fActionGroups.fillActionBars(actionBars);
		
		if (fHasCustomFilter)
			fCustomFiltersActionGroup.fillActionBars(actionBars);
	}
	
	//---- IWorkbenchPart ------------------------------------------------------


	public void setFocus() {
		fViewer.getControl().setFocus();
	}
	
	public void dispose() {
		if (fViewer != null) {
			getViewSite().getWorkbenchWindow().getSelectionService().removePostSelectionListener(this);
			getViewSite().getPage().removePartListener(fPartListener);
			fViewer= null;
		}
		if (fActionGroups != null)
			fActionGroups.dispose();
		super.dispose();
	}
	
	/**
	 * Adds the KeyListener
	 */
	protected void addKeyListener() {
		fViewer.getControl().addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent event) {
				handleKeyReleased(event);
			}
		});
	}

	protected void handleKeyReleased(KeyEvent event) {
		if (event.stateMask != 0) 
			return;		
		
		int key= event.keyCode;
		IAction action;
		if (key == SWT.F5) {
			action= fBuildActionGroup.getRefreshAction();
			if (action.isEnabled())
				action.run();
		} if (event.character == SWT.DEL) {
			action= fCCPActionGroup.getDeleteAction();
			if (action.isEnabled())
				action.run();
		}
	}
	
	//---- Adding Action to Toolbar -------------------------------------------
	
	protected void fillToolBar(IToolBarManager tbm) {
	}	

	/**
	 * Called when the context menu is about to open.
	 * Override to add your own context dependent menu contributions.
	 */
	public void menuAboutToShow(IMenuManager menu) {
		JavaPlugin.createStandardGroups(menu);
		
		IStructuredSelection selection= (IStructuredSelection) fViewer.getSelection();
		int size= selection.size();		
		Object element= selection.getFirstElement();
		IJavaElement jElement= element instanceof IJavaElement ? (IJavaElement)element : null;
		
		if (size == 0 || (size == 1 && (isNewTarget(jElement) || element instanceof IContainer))) {
			MenuManager newMenu= new MenuManager(PackagesMessages.getString("PackageExplorer.new")); //$NON-NLS-1$
			menu.appendToGroup(IContextMenuConstants.GROUP_NEW, newMenu);
			new NewWizardMenu(newMenu, getSite().getWorkbenchWindow(), false);
		}

		if (size == 1)
			addOpenNewWindowAction(menu, element);
		fActionGroups.setContext(new ActionContext(selection));
		fActionGroups.fillContextMenu(menu);
		fActionGroups.setContext(null);
	}

	private boolean isNewTarget(IJavaElement element) {
		if (element == null)
			return false;
		int type= element.getElementType();
		return type == IJavaElement.JAVA_PROJECT ||
			type == IJavaElement.PACKAGE_FRAGMENT_ROOT || 
			type == IJavaElement.PACKAGE_FRAGMENT ||
			type == IJavaElement.COMPILATION_UNIT ||
			type == IJavaElement.TYPE;
	}
	
	private void addOpenNewWindowAction(IMenuManager menu, Object element) {
		if (element instanceof IJavaElement) {
			try {
				element= ((IJavaElement)element).getCorrespondingResource();
			} catch(JavaModelException e) {
			}
		}
		if (!(element instanceof IContainer))
			return;
		menu.appendToGroup(
			IContextMenuConstants.GROUP_OPEN, 
			new PatchedOpenInNewWindowAction(getSite().getWorkbenchWindow(), (IContainer)element));
	}

	protected void createActions() {		
		fActionGroups= new CompositeActionGroup(new ActionGroup[] {
				fOpenEditorGroup= new OpenEditorActionGroup(this), 
				new OpenViewActionGroup(this), 
				new ShowActionGroup(this), 
				fCCPActionGroup= new CCPActionGroup(this), 
				new RefactorActionGroup(this),
				new ImportActionGroup(this),
				new GenerateActionGroup(this),
				fBuildActionGroup= new BuildActionGroup(this),
				new JavaSearchActionGroup(this)});

		String viewId= getConfigurationElement().getAttribute("id"); //$NON-NLS-1$
		Assert.isNotNull(viewId);
		
		IPropertyChangeListener titleUpdater= new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				String property= event.getProperty();
				if (IWorkingSetManager.CHANGE_WORKING_SET_NAME_CHANGE.equals(property))
					updateTitle();
			}
		};
		if (fHasWorkingSetFilter)
			fWorkingSetFilterActionGroup= new WorkingSetFilterActionGroup(fViewer, viewId, getShell(), titleUpdater);
	}
	
	/**
	 * Returns the shell to use for opening dialogs.
	 * Used in this class, and in the actions.
	 */
	private Shell getShell() {
		return fViewer.getControl().getShell();
	}

	protected final Display getDisplay() {
		return fViewer.getControl().getDisplay();
	}	

	/**
	 * Returns the selection provider.
	 */
	ISelectionProvider getSelectionProvider() {
		return fViewer;
	}

	/**
	 * Answers if the given <code>element</code> is a valid
	 * input for this part.
	 * 
	 * @param 	element	the object to test
	 * @return	<true> if the given element is a valid input
	 */
	abstract protected boolean isValidInput(Object element);

	/**
	 * Answers if the given <code>element</code> is a valid
	 * element for this part.
	 * 
	 * @param 	element	the object to test
	 * @return	<true> if the given element is a valid element
	 */
	protected boolean isValidElement(Object element) {
		if (element == null)
			return false;
		element= getSuitableJavaElement(element);
		if (element == null)
			return false;
		Object input= getViewer().getInput();
		if (input == null)
			return false;
		if (input instanceof Collection)
			return ((Collection)input).contains(element);
		else
			return input.equals(element);

	}

	private boolean isInputResetBy(Object newInput, Object input, IWorkbenchPart part) {
		if (newInput == null)
			return part == fPreviousSelectionProvider;
			
		if (input instanceof IJavaElement && newInput instanceof IJavaElement)
			return getTypeComparator().compare(newInput, input)  > 0;
		else
			return false;
	}

	private boolean isInputResetBy(IWorkbenchPart part) {
		if (!(part instanceof JavaBrowsingPart))
			return true;
		Object thisInput= getViewer().getInput();
		Object partInput= ((JavaBrowsingPart)part).getViewer().getInput();
		if (thisInput instanceof IJavaElement && partInput instanceof IJavaElement)
			return getTypeComparator().compare(partInput, thisInput) > 0;
		else
			return true;
	}

	protected boolean isAncestorOf(Object ancestor, Object element) {
		if (element instanceof IJavaElement && ancestor instanceof IJavaElement)
			return !element.equals(ancestor) && internalIsAncestorOf((IJavaElement)ancestor, (IJavaElement)element);
		return false;
	}
	
	private boolean internalIsAncestorOf(IJavaElement ancestor, IJavaElement element) {
		if (element != null)
			return element.equals(ancestor) || internalIsAncestorOf(ancestor, element.getParent());
		else
			return false;
	}
	
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		
		if (!fProcessSelectionEvents || part == this || part instanceof ISearchResultView || !(selection instanceof IStructuredSelection))
			return;
		
		// Set selection
		Object selectedElement= getSingleElementFromSelection(selection);
		
		if (selectedElement != null && part.equals(fPreviousSelectionProvider) && selectedElement.equals(fPreviousSelectedElement))
			return;
		fPreviousSelectedElement= selectedElement;
		
		Object currentInput= (IJavaElement)getViewer().getInput();
		if (selectedElement != null && selectedElement.equals(currentInput)) {
			IJavaElement elementToSelect= findElementToSelect(getSingleElementFromSelection(selection));
			if (elementToSelect != null && getTypeComparator().compare(selectedElement, elementToSelect) < 0)
				setSelection(new StructuredSelection(elementToSelect), true);
			fPreviousSelectionProvider= part;
			return;
		}
		
		// Clear input if needed
		if (part != fPreviousSelectionProvider && selectedElement != null && !selectedElement.equals(currentInput) && isInputResetBy(selectedElement, currentInput, part)) {
			if (!isAncestorOf(selectedElement, currentInput))
				setInput(null);
			fPreviousSelectionProvider= part;
			return;
		} else	if (selection.isEmpty() && !isInputResetBy(part)) {
			fPreviousSelectionProvider= part;
			return;
		} else if (selectedElement == null && part == fPreviousSelectionProvider) {
			setInput(null);
			fPreviousSelectionProvider= part;
			return;
		}
		fPreviousSelectionProvider= part;
		
		// Adjust input and set selection and 
		if (selectedElement instanceof IJavaElement)
			adjustInputAndSetSelection((IJavaElement)selectedElement);
		else
			setSelection(StructuredSelection.EMPTY, true);
	}


	void setHasWorkingSetFilter(boolean state) {
		fHasWorkingSetFilter= state;
	}

	void setHasCustomSetFilter(boolean state) {
		fHasCustomFilter= state;
	}
	
	protected void setInput(Object input) {
		setViewerInput(input);
		updateTitle();
	}

	private void setViewerInput(Object input) {
		fProcessSelectionEvents= false;
		fViewer.setInput(input);
		fProcessSelectionEvents= true;
	}

	void updateTitle() {
		setTitleToolTip(getToolTipText(fViewer.getInput()));
	}

	/**
	 * Returns the tool tip text for the given element.
	 */
	String getToolTipText(Object element) {
		String result;
		if (!(element instanceof IResource)) {
			result= JavaElementLabels.getTextLabel(element, AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS);		
		} else {
			IPath path= ((IResource) element).getFullPath();
			if (path.isRoot()) {
				result= getConfigurationElement().getAttribute("name"); //$NON-NLS-1$
			} else {
				result= path.makeRelative().toString();
			}
		}
		
		if (fWorkingSetFilterActionGroup == null || fWorkingSetFilterActionGroup.getWorkingSet() == null)
			return result;

		IWorkingSet ws= fWorkingSetFilterActionGroup.getWorkingSet();
		String wsstr= JavaBrowsingMessages.getFormattedString("JavaBrowsingPart.toolTip", new String[] { ws.getName() }); //$NON-NLS-1$
		if (result.length() == 0)
			return wsstr;
		return JavaBrowsingMessages.getFormattedString("JavaBrowsingPart.toolTip2", new String[] { result, ws.getName() }); //$NON-NLS-1$
	}
	
	public String getTitleToolTip() {
		if (fViewer == null)
			return super.getTitleToolTip();
		return getToolTipText(fViewer.getInput());
	}

	protected final StructuredViewer getViewer() {
		return fViewer;
	}

	protected ILabelProvider createLabelProvider() {
		return new AppearanceAwareLabelProvider(
						AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS,
						AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS | JavaElementImageProvider.SMALL_ICONS,
						AppearanceAwareLabelProvider.getDecorators(true, null)
						);
	}

	protected ILabelProvider createTitleProvider() {
		return new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_BASICS | JavaElementLabelProvider.SHOW_SMALL_ICONS);
	}

	protected final ILabelProvider getLabelProvider() {
		return fLabelProvider;
	}

	protected final ILabelProvider getTitleProvider() {
		return fTitleProvider;
	}

	/**
	 * Creates the the viewer of this part.
	 * 
	 * @param parent	the parent for the viewer
	 */
	protected StructuredViewer createViewer(Composite parent) {
		return new ProblemTableViewer(parent, SWT.MULTI);
	}
	
	protected int getLabelProviderFlags() {
		return JavaElementLabelProvider.SHOW_BASICS | JavaElementLabelProvider.SHOW_OVERLAY_ICONS |
				JavaElementLabelProvider.SHOW_SMALL_ICONS | JavaElementLabelProvider.SHOW_VARIABLE | JavaElementLabelProvider.SHOW_PARAMETERS;
	}

	/**
	 * Adds filters the viewer of this part.
	 */
	protected void addFilters() {
		// default is to have no filters
	}

	/**
	 * Creates the the content provider of this part.
	 */
	protected StandardJavaElementContentProvider createContentProvider() {
		return new JavaBrowsingContentProvider(true, this); //
	}

	protected void setInitialInput() {
		// Use the selection, if any
		ISelection selection= getSite().getPage().getSelection();
		Object input= getSingleElementFromSelection(selection);
		if (!(input instanceof IJavaElement)) {
			// Use the input of the page
			input= getSite().getPage().getInput();
			if (!(input instanceof IJavaElement) && input instanceof IAdaptable)
				input= ((IAdaptable)input).getAdapter(IJavaElement.class);
		}
		setInput(findInputForJavaElement((IJavaElement)input));		
	}

	protected void setInitialSelection() {
		// Use the selection, if any
		Object input;
		ISelection selection= getSite().getPage().getSelection();
		if (selection != null && !selection.isEmpty())
			input= getSingleElementFromSelection(selection);
		else {
			// Use the input of the page
			input= getSite().getPage().getInput();
			if (!(input instanceof IJavaElement)) {
				if (input instanceof IAdaptable)
					input= ((IAdaptable)input).getAdapter(IJavaElement.class);
				else
					return;
			}
		}
		if (findElementToSelect((IJavaElement)input) != null)
			adjustInputAndSetSelection((IJavaElement)input);
	}

	final protected void setHelp() {
		JavaUIHelp.setHelp(fViewer, getHelpContextId());
	}

	/**
	 * Returns the context ID for the Help system
	 * 
	 * @return	the string used as ID for the Help context
	 */
	abstract protected String getHelpContextId();

	/**
	 * Adds additional listeners to this view.
	 * This method can be overridden but should
	 * call super.
	 */
	protected void hookViewerListeners() {
		fViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				if (!fProcessSelectionEvents)
					return;

				IWorkbenchPage page= getSite().getPage();
				if (page == null)
					return;

				if (page.equals(JavaPlugin.getActivePage()) && JavaBrowsingPart.this.equals(page.getActivePart())) {
					linkToEditor((IStructuredSelection)event.getSelection());
				}
			}
		});

		fViewer.addOpenListener(new IOpenListener() {
			public void open(OpenEvent event) {
				IAction open= fOpenEditorGroup.getOpenAction();
				if (open.isEnabled())
					open.run();
			}
		});
	}

	void adjustInputAndSetSelection(IJavaElement je) {
		je= getSuitableJavaElement(je);
		IJavaElement elementToSelect= findElementToSelect(je);
		IJavaElement newInput= findInputForJavaElement(je);
		if (elementToSelect == null && !isValidInput(newInput))
			// Clear input
			setInput(null);
		else if (elementToSelect == null || getViewer().testFindItem(elementToSelect) == null)
			// Adjust input to selection
			setInput(findInputForJavaElement(je));
		
		if (elementToSelect != null)
			setSelection(new StructuredSelection(elementToSelect), true);
		else
			setSelection(StructuredSelection.EMPTY, true);
	}

	/**
	 * Finds the closest Java element which can be used as input for
	 * this part and has the given Java element as child
	 * 
	 * @param 	je 	the Java element for which to search the closest input
	 * @return	the closest Java element used as input for this part
	 */
	protected IJavaElement findInputForJavaElement(IJavaElement je) {
		if (je == null || !je.exists())
			return null;
		if (isValidInput(je))
			return je;
		return findInputForJavaElement(je.getParent());
	}
	
	final protected IJavaElement findElementToSelect(Object obj) {
		if (obj instanceof IJavaElement)
			return findElementToSelect((IJavaElement)obj);
		return null;
	}
	
	/**
	 * Finds the element which has to be selected in this part.
	 * 
	 * @param je	the Java element which has the focus
	 */
	abstract protected IJavaElement findElementToSelect(IJavaElement je);
	

	private Object getSingleElementFromSelection(ISelection selection) {
		if (!(selection instanceof StructuredSelection) || selection.isEmpty())
			return null;
		
		Iterator iter= ((StructuredSelection)selection).iterator();
		Object firstElement= iter.next();
		if (!(firstElement instanceof IJavaElement)) {
			if (firstElement instanceof IAdaptable)
				return (IJavaElement)((IAdaptable)firstElement).getAdapter(IJavaElement.class);
			else
				return firstElement;
		}
		Object currentInput= (IJavaElement)getViewer().getInput();
		if (currentInput == null || !currentInput.equals(findInputForJavaElement((IJavaElement)firstElement)))
			if (iter.hasNext())
				// multi selection and view is empty
				return null;
			else
				// ok: single selection and view is empty 
				return firstElement;

		// be nice to multi selection
		while (iter.hasNext()) {
			Object element= iter.next();
			if (!(element instanceof IJavaElement))
				return null;
			if (!currentInput.equals(findInputForJavaElement((IJavaElement)element)))
				return null;
		}
		return firstElement;
	}

	/**
	 * Gets the typeComparator.
	 * @return Returns a JavaElementTypeComparator
	 */
	protected Comparator getTypeComparator() {
		return fTypeComparator;
	}

	/**
	 * Links to editor (if option enabled)
	 */
	private void linkToEditor(IStructuredSelection selection) {	
		Object obj= selection.getFirstElement();
		Object element= null;

		if (selection.size() == 1) {
			IEditorPart part= EditorUtility.isOpenInEditor(obj);
			if (part != null) {
				IWorkbenchPage page= getSite().getPage();
				page.bringToTop(part);
				if (obj instanceof IJavaElement) 
					EditorUtility.revealInEditor(part, (IJavaElement) obj);
			}
		}
	}

	private void setSelectionFromEditor(IWorkbenchPart part) {
		if (part == null)
			return;
		IWorkbenchPartSite site= part.getSite();
		if (site == null)
			return;
		ISelectionProvider provider= site.getSelectionProvider();
		if (provider != null)
			setSelectionFromEditor(part, provider.getSelection());
	}

	private void setSelectionFromEditor(IWorkbenchPart part, ISelection selection) {
		if (part instanceof IEditorPart && JavaBasePreferencePage.linkBrowsingViewSelectionToEditor()) {
			IEditorInput ei= ((IEditorPart)part).getEditorInput();
			if (selection instanceof ITextSelection) {
				int offset= ((ITextSelection)selection).getOffset();
				IJavaElement element= getElementForInputAt(ei, offset);
				if (element != null) {
					adjustInputAndSetSelection(element);
					return;
				}
			}
			if (ei instanceof IFileEditorInput) {
				IFile file= ((IFileEditorInput)ei).getFile();
				IJavaElement je= (IJavaElement)file.getAdapter(IJavaElement.class);
				if (je == null) {
					setSelection(null, false);
					return;
				}
				adjustInputAndSetSelection(je);

			} else if (ei instanceof IClassFileEditorInput) {
				IClassFile cf= ((IClassFileEditorInput)ei).getClassFile();
				adjustInputAndSetSelection(cf);
			}
			return;
		}
	}
	
	/**
	 * Returns the element contained in the EditorInput
	 */
	Object getElementOfInput(IEditorInput input) {
		if (input instanceof IClassFileEditorInput)
			return ((IClassFileEditorInput)input).getClassFile();
		else if (input instanceof IFileEditorInput)
			return ((IFileEditorInput)input).getFile();
		else if (input instanceof JarEntryEditorInput)
			return ((JarEntryEditorInput)input).getStorage();
		return null;
	}
	
	private IResource getResourceFor(Object element) {
		if (element instanceof IJavaElement) {
			if (element instanceof IWorkingCopy) {
				IWorkingCopy wc= (IWorkingCopy)element;
				IJavaElement original= wc.getOriginalElement();
				if (original != null)
					element= original;
			}
			try {
				element= ((IJavaElement)element).getUnderlyingResource();
			} catch (JavaModelException e) {
				return null;
			}
		}
		if (!(element instanceof IResource) || ((IResource)element).isPhantom()) {
			return null;
		}
		return (IResource)element;
	}

	private void setSelection(ISelection selection, boolean reveal) {
		if (selection != null && selection.equals(fViewer.getSelection()))
			return;
		fProcessSelectionEvents= false;
		fViewer.setSelection(selection, reveal);
		fProcessSelectionEvents= true;
	}

	/**
	 * Tries to find the given element in a workingcopy.
	 */
	protected static IJavaElement getWorkingCopy(IJavaElement input) {
		try {
			if (input instanceof ICompilationUnit)
				return ((ICompilationUnit)input).findSharedWorkingCopy(JavaUI.getBufferFactory());
			else
				return EditorUtility.getWorkingCopy(input, false);
		} catch (JavaModelException ex) {
		}
		return null;
	}

	/**
	 * Returns the original element from which the specified working copy
	 * element was created from. This is a handle only method, the
	 * returned element may or may not exist.
	 * 
	 * @param	workingCopy the element for which to get the original
	 * @return the original Java element or <code>null</code> if this is not a working copy element
	 */
	protected static IJavaElement getOriginal(IJavaElement workingCopy) {
		ICompilationUnit cu= getCompilationUnit(workingCopy);
		if (cu != null)
			return ((IWorkingCopy)cu).getOriginal(workingCopy);
		return null;
	}

	/**
	 * Returns the compilation unit for the given java element.
	 * 
	 * @param	element the java element whose compilation unit is searched for
	 * @return	the compilation unit of the given java element
	 */
	protected static ICompilationUnit getCompilationUnit(IJavaElement element) {
		if (element == null)
			return null;
			
		if (element instanceof IMember)
			return ((IMember) element).getCompilationUnit();
		
		int type= element.getElementType();
		if (IJavaElement.COMPILATION_UNIT == type)
			return (ICompilationUnit) element;
		if (IJavaElement.CLASS_FILE == type)
			return null;
			
		return getCompilationUnit(element.getParent());
	}

	/**
	 * Converts the given Java element to one which is suitable for this
	 * view. It takes into account wether the view shows working copies or not.
	 *
	 * @param	element the Java element to be converted
	 * @return	an element suitable for this view
	 */
	protected IJavaElement getSuitableJavaElement(Object obj) {
		if (!(obj instanceof IJavaElement))
			return null;
		IJavaElement element= (IJavaElement)obj;
		if (fTypeComparator.compare(element, IJavaElement.COMPILATION_UNIT) > 0)
			return element;
		if (element.getElementType() == IJavaElement.CLASS_FILE)
			return element;
		if (((StandardJavaElementContentProvider)getViewer().getContentProvider()).getProvideWorkingCopy()) {
			IJavaElement wc= getWorkingCopy(element);
			if (wc != null)
				element= wc;
			return element;
		}
		else {
			ICompilationUnit cu= getCompilationUnit(element);
			if (cu != null && ((IWorkingCopy)cu).isWorkingCopy())
				return ((IWorkingCopy)cu).getOriginal(element);
			else
				return element;
		}
	}

	/**
	 * @see JavaEditor#getElementAt(int)
	 */
	protected IJavaElement getElementForInputAt(IEditorInput input, int offset) {
		if (input instanceof IClassFileEditorInput) {
			try {
				return ((IClassFileEditorInput)input).getClassFile().getElementAt(offset);
			} catch (JavaModelException ex) {
				return null;
			}
		}

		IWorkingCopyManager manager= JavaPlugin.getDefault().getWorkingCopyManager();
		ICompilationUnit unit= manager.getWorkingCopy(input);
		if (unit != null)
			try {
				unit.reconcile();
				return unit.getElementAt(offset);
			} catch (JavaModelException ex) {
			}
		return null;
	}
	
	protected IType getTypeForCU(ICompilationUnit cu) {
		cu= (ICompilationUnit)getSuitableJavaElement(cu);
		
		// Use primary type if possible
		IType primaryType= cu.findPrimaryType();
		if (primaryType != null)
			return primaryType;

		// Use first top-level type
		try {
			IType[] types= cu.getTypes();
			if (types.length > 0)
				return types[0];
			else
				return null;
		} catch (JavaModelException ex) {
			return null;
		}
	}	

	void setProcessSelectionEvents(boolean state) {
		fProcessSelectionEvents= state;
	}
}
