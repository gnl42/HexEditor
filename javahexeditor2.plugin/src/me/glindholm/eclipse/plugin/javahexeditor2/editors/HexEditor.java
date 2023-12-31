/*
 * javahexeditor, a java hex editor
 * Copyright (C) 2006, 2009 Jordi Bergenthal, pestatije(-at_)users.sourceforge.net
 * Copyright (C) 2018 - 2021 Peter Dell, peterdell(-at_)users.sourceforge.net
 * The official javahexeditor site is https://sourceforge.net/projects/javahexeditor
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package me.glindholm.eclipse.plugin.javahexeditor2.editors;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.editors.text.ILocationProvider;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import me.glindholm.eclipse.plugin.javahexeditor2.BinaryContent;
import me.glindholm.eclipse.plugin.javahexeditor2.BinaryContent.RangeSelection;
import me.glindholm.eclipse.plugin.javahexeditor2.HexEditorPlugin;
import me.glindholm.eclipse.plugin.javahexeditor2.HexTexts;
import me.glindholm.eclipse.plugin.javahexeditor2.Manager;
import me.glindholm.eclipse.plugin.javahexeditor2.Preferences;
import me.glindholm.eclipse.plugin.javahexeditor2.Texts;
import me.glindholm.eclipse.plugin.javahexeditor2.common.Log;
import me.glindholm.eclipse.plugin.javahexeditor2.common.TextUtility;

public final class HexEditor extends EditorPart implements ISelectionProvider {

    private static class MyAction extends Action {
        private final Manager manager;
        private final String myId;

        public MyAction(final Manager manager, final String id) {
            if (manager == null) {
                throw new IllegalArgumentException("Parameter 'manager' must not be null.");
            }
            if (id == null) {
                throw new IllegalArgumentException("Parameter 'id' must not be null.");
            }
            this.manager = manager;
            myId = id;
        }

        @Override
        public void run() {
            if (myId.equals(ActionFactory.UNDO.getId())) {
                manager.doUndo();
            } else if (myId.equals(ActionFactory.REDO.getId())) {
                manager.doRedo();
            } else if (myId.equals(ActionFactory.CUT.getId())) {
                manager.doCut();
            } else if (myId.equals(ActionFactory.COPY.getId())) {
                manager.doCopy();
            } else if (myId.equals(ActionFactory.PASTE.getId())) {
                manager.doPaste();
            } else if (myId.equals(ActionFactory.DELETE.getId())) {
                manager.doDelete();
            } else if (myId.equals(ActionFactory.SELECT_ALL.getId())) {
                manager.doSelectAll();
            } else if (myId.equals(ActionFactory.FIND.getId())) {
                manager.doFind();
            }
        }
    }

    // Public id from the contributions.
    public static final String ID = "me.glindholm.eclipse.plugin.javahexeditor2"; //$NON-NLS-1$

    // Private IDs from the contributions.
    private static final String OUTLINE_ELEMENT_ATTRIBUTE_CLASS = "class"; //$NON-NLS-1$
    private static final String OUTLINE_ELEMENT_NAME = "outline"; //$NON-NLS-1$
    private static final String OUTLINE_ID = "me.glindholm.eclipse.plugin.javahexeditor2.outline"; //$NON-NLS-1$

    Manager manager;
    private IContentOutlinePage outlinePage;
    private IPropertyChangeListener preferencesChangeListener;
    Set<ISelectionChangedListener> selectionListeners;

    private IStatusLineManager statusLineManager;

    private HexEditorInput hexEditorInput;
    private HexTexts hexTexts;

    public HexEditor() {
        manager = new Manager((contentFile, monitor) -> {
            final IWorkspace workspace = ResourcesPlugin.getWorkspace();

            final Path p = new Path(contentFile.getAbsolutePath());
            final IWorkspaceRoot r = workspace.getRoot();
            final IFile file = r.getFileForLocation(p);
            if (file != null && file.exists()) {
                try {
                    file.appendContents(new ByteArrayInputStream(new byte[0]), true, true, monitor);
                } catch (final CoreException ex) {
                    throw new IOException(
                            TextUtility.format(Texts.MANAGER_SAVE_MESSAGE_CANNOT_READ_FROM_SAVED_FILE, contentFile.getAbsolutePath(), ex.getMessage()));
                }

            }
        });

    }

    @Override
    public void addSelectionChangedListener(final ISelectionChangedListener listener) {
        if (listener == null) {
            return;
        }

        if (selectionListeners == null) {
            selectionListeners = new HashSet<>();
        }
        selectionListeners.add(listener);
    }

    @Override
    public void createPartControl(final Composite parent) {

        statusLineManager = getEditorSite().getActionBars().getStatusLineManager();

        final HexEditorPlugin plugin = HexEditorPlugin.getDefault();
        getManager().setTextFont(HexEditorPreferences.getFontData());
        manager.setFindReplaceHistory(plugin.getFindReplaceHistory());
        hexTexts = manager.createEditorPart(parent);
        final FillLayout fillLayout = new FillLayout();
        parent.setLayout(fillLayout);

        // Free previously allocated temporary resources.
        if (hexEditorInput != null) {
            hexEditorInput.dispose();
        }
        hexEditorInput = new HexEditorInput();
        try {
            hexEditorInput.open(getEditorInput());
            manager.openFile(hexEditorInput.getContentFile(), hexEditorInput.getCharset());
        } catch (final CoreException ex) {
            HexEditorPlugin.getDefault().getLog().log(ex.getStatus());
            statusLineManager.setErrorMessage(ex.getMessage());
        }

        setPartName(hexEditorInput.getInputName());

        // Register any global actions with the site's IActionBars.
        final IActionBars bars = getEditorSite().getActionBars();
        String id = ActionFactory.UNDO.getId();
        bars.setGlobalActionHandler(id, new MyAction(manager, id));
        id = ActionFactory.REDO.getId();
        bars.setGlobalActionHandler(id, new MyAction(manager, id));
        id = ActionFactory.CUT.getId();
        bars.setGlobalActionHandler(id, new MyAction(manager, id));
        id = ActionFactory.COPY.getId();
        bars.setGlobalActionHandler(id, new MyAction(manager, id));
        id = ActionFactory.PASTE.getId();
        bars.setGlobalActionHandler(id, new MyAction(manager, id));
        id = ActionFactory.DELETE.getId();
        bars.setGlobalActionHandler(id, new MyAction(manager, id));
        id = ActionFactory.SELECT_ALL.getId();
        bars.setGlobalActionHandler(id, new MyAction(manager, id));
        id = ActionFactory.FIND.getId();
        bars.setGlobalActionHandler(id, new MyAction(manager, id));

        manager.addListener(event -> {
            firePropertyChange(PROP_DIRTY);
            updateActionsStatus();
        });

        bars.updateActionBars();

        preferencesChangeListener = event -> {
            if (Preferences.FONT_DATA.equals(event.getProperty())) {
                manager.setTextFont((FontData) event.getNewValue());
            }
        };
        final IPreferenceStore store = plugin.getPreferenceStore();
        store.addPropertyChangeListener(preferencesChangeListener);

        manager.addLongSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                Log.trace(this, "Long selection: {0}", e);
                if (selectionListeners == null) {
                    return;
                }

                final long[] longSelection = manager.getLongSelection(e);
                final SelectionChangedEvent event = new SelectionChangedEvent(HexEditor.this,
                        new StructuredSelection(new Object[] { Long.valueOf(longSelection[0]), Long.valueOf(longSelection[1]) }));
                for (final ISelectionChangedListener listener : selectionListeners) {
                    listener.selectionChanged(event);
                }
            }
        });

    }

    @Override
    public void dispose() {
        final IPreferenceStore store = HexEditorPlugin.getDefault().getPreferenceStore();
        if (preferencesChangeListener != null) {
            store.removePropertyChangeListener(preferencesChangeListener);
        }
        if (hexTexts != null) {
            hexTexts.dispose();
        }
        if (hexEditorInput != null) {
            hexEditorInput.dispose();
        }
    }

    @Override
    public void doSave(final IProgressMonitor monitor) {
        final File file = getManager().getContentFile();
        if (file == null) {
            doSaveAs();
        } else {
            saveToFile(file, false, monitor);
        }
    }

    @Override
    public void doSaveAs() {
        saveAsToFile(false);
    }

    @SuppressWarnings("unchecked") // for return (T) result;
    @Override
    public <T> T getAdapter(final Class<T> required) {
        Object result = null;
        if (IContentOutlinePage.class.isAssignableFrom(required)) {
            if (outlinePage == null) {
                outlinePage = getOutlinePage();
            }
            result = outlinePage;
        } else if (BinaryContent.class.isAssignableFrom(required)) {
            result = getManager().getContent();
        } else if (Manager.class.isAssignableFrom(required)) {
            result = getManager();
        } else {
            result = super.getAdapter(required);
        }
        return (T) result;
    }

    /**
     * Getter for the manager instance.
     *
     * @return the manager
     */
    public Manager getManager() {
        return manager;
    }

    IContentOutlinePage getOutlinePage() {
        final IExtensionRegistry registry = Platform.getExtensionRegistry();
        final IExtensionPoint point = registry.getExtensionPoint(OUTLINE_ID);
        if (point == null) {
            return null;
        }

        final IExtension[] extensions = point.getExtensions();
        if (extensions.length == 0) {
            return null;
        }
        final IConfigurationElement[] elements = extensions[0].getConfigurationElements();
        String className = null;
        for (final IConfigurationElement element : elements) {
            if (OUTLINE_ELEMENT_NAME.equals(element.getName())) {
                className = element.getAttribute(OUTLINE_ELEMENT_ATTRIBUTE_CLASS);
                break;
            }
        }

        final Bundle aBundle = Platform.getBundle(extensions[0].getNamespaceIdentifier());
        IContentOutlinePage result = null;
        if (aBundle != null) {
            try {
                aBundle.start();
            } catch (final BundleException e) {
                return null;
            }
            try {
                // throws IllegalAccessException, InstantiationException,
                // ClassNotFoundException
                result = (IContentOutlinePage) aBundle.loadClass(className).getConstructor().newInstance();
            } catch (final Exception e) {
                return null;
            }
        }

        return result;
    }

    @Override
    public ISelection getSelection() {
        final RangeSelection rangeSelection = getManager().getSelection();
        return new StructuredSelection(new Object[] { Long.valueOf(rangeSelection.start), Long.valueOf(rangeSelection.end) });
    }

    @Override
    public void init(final IEditorSite site, final IEditorInput input) throws PartInitException {
        Log.trace(this, "init starts with selection provider {0}", site.getSelectionProvider());

        setSite(site);
        if (!(input instanceof IPathEditorInput) && !(input instanceof ILocationProvider) && !(input instanceof IURIEditorInput)
                && !(input instanceof IStorageEditorInput)) {
            throw new PartInitException("Input '" + input.toString() + "'is not a file"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        setInput(input);
        // When opening an external file the workbench (Eclipse 3.1) calls
        // HexEditorActionBarContributor.
        // MyStatusLineContributionItem.fill() before
        // HexEditorActionBarContributor.setActiveEditor()
        // but we need an editor to fill the status bar.
        site.getActionBarContributor().setActiveEditor(this);
        site.setSelectionProvider(this);

    }

    @Override
    public boolean isDirty() {
        return getManager().isDirty();
    }

    @Override
    public boolean isSaveAsAllowed() {
        return true;
    }

    @Override
    public void removeSelectionChangedListener(final ISelectionChangedListener listener) {
        if (selectionListeners != null) {
            selectionListeners.remove(listener);
        }
    }

    void saveAsToFile(final boolean selection) {
        final File file = getManager().showSaveAsDialog(getEditorSite().getShell(), selection);
        if (file == null) {
            return;
        }

        final IRunnableWithProgress runnable = monitor -> saveToFile(file, selection, monitor);
        final ProgressMonitorDialog monitorDialog = new ProgressMonitorDialog(getEditorSite().getShell());
        try {
            monitorDialog.run(false, false, runnable);
        } catch (final InvocationTargetException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    void saveToFile(final File file, final boolean selection, final IProgressMonitor monitor) {
        monitor.beginTask(Texts.EDITOR_MESSAGE_SAVING_FILE_PLEASE_WAIT, IProgressMonitor.UNKNOWN);
        try {
            if (selection) {
                manager.doSaveSelectionAs(file);
            } else {
                manager.saveAsFile(file, monitor);
            }
        } catch (final IOException ex) {
            monitor.setCanceled(true);
            monitor.done();
            statusLineManager.setErrorMessage(ex.getMessage());
            return;
        }
        monitor.done();
        if (!selection) {
            setPartName(file.getName());
            firePropertyChange(PROP_DIRTY);
        }

        statusLineManager.setMessage(TextUtility.format(Texts.EDITOR_MESSAGE_FILE_SAVED, file.getAbsolutePath()));
    }

    @Override
    public void setFocus() {
        // useless. It is called before ActionBarContributor.setActiveEditor()
        // so focusing is done there
        hexTexts.setFocus();
    }

    @Override
    public void setSelection(final ISelection selection) {
        if (selection.isEmpty()) {
            return;
        }
        final StructuredSelection aSelection = (StructuredSelection) selection;
        long[] startEnd = (long[]) aSelection.getFirstElement();
        final long start = startEnd[0];
        long end = start;
        if (startEnd.length > 1) {
            end = startEnd[1];
        }
        if (aSelection.size() > 1) {
            startEnd = (long[]) aSelection.toArray()[1];
            end = startEnd[0];
            if (startEnd.length > 1) {
                end = startEnd[1];
            }
        }
        getManager().setSelection(new RangeSelection(start, end));
    }

    /**
     * Updates the status of actions: enables/disables them depending on whether there is text selected
     * and whether inserting or overwriting is active. Undo/redo actions are enabled/disabled as well.
     */
    void updateActionsStatus() {
        final boolean textSelected = getManager().isTextSelected();
        final boolean lengthModifiable = textSelected && !manager.isOverwriteMode();
        final boolean filled = getManager().isFilled();
        final IActionBars bars = getEditorSite().getActionBars();
        IAction action = bars.getGlobalActionHandler(ActionFactory.UNDO.getId());
        if (action != null) {
            action.setEnabled(manager.canUndo());
        }

        action = bars.getGlobalActionHandler(ActionFactory.REDO.getId());
        if (action != null) {
            action.setEnabled(manager.canRedo());
        }

        action = bars.getGlobalActionHandler(ActionFactory.CUT.getId());
        if (action != null) {
            action.setEnabled(lengthModifiable);
        }

        action = bars.getGlobalActionHandler(ActionFactory.COPY.getId());
        if (action != null) {
            action.setEnabled(textSelected);
        }

        action = bars.getGlobalActionHandler(ActionFactory.DELETE.getId());
        if (action != null) {
            action.setEnabled(lengthModifiable);
        }

        action = bars.getGlobalActionHandler(ActionFactory.SELECT_ALL.getId());
        if (action != null) {
            action.setEnabled(filled);
        }

        action = bars.getGlobalActionHandler(ActionFactory.FIND.getId());
        if (action != null) {
            action.setEnabled(filled);
        }

        bars.updateActionBars();
    }
}
