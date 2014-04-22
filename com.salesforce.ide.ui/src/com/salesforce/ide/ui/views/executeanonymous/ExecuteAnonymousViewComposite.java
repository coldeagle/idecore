/*******************************************************************************
 * Copyright (c) 2014 Salesforce.com, inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Salesforce.com, inc. - initial API and implementation
 ******************************************************************************/
package com.salesforce.ide.ui.views.executeanonymous;

import java.util.*;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.salesforce.ide.core.internal.context.ContainerDelegate;
import com.salesforce.ide.core.internal.utils.LoggingInfo;
import com.salesforce.ide.core.internal.utils.Utils;
import com.salesforce.ide.core.remote.apex.ExecuteAnonymousResultExt;
import com.salesforce.ide.ui.internal.composite.BaseComposite;
import com.salesforce.ide.ui.internal.utils.UIUtils;
import com.salesforce.ide.ui.views.LoggingComposite;

/**
 * Legacy class
 * 
 * @author dcarroll
 */
public class ExecuteAnonymousViewComposite extends BaseComposite {

    protected SashForm sashForm = null;
    protected Composite cmpSource = null;
    protected Composite cmpResult = null;
    protected Button btnExecute = null;
    protected StyledText txtSourceInput = null;
    protected StyledText txtResult = null;
    protected Composite cmpProject = null;
    protected Combo cboProject = null;
    protected ExecuteAnonymousController executeAnonymousController = null;
    protected LoggingComposite loggingComposite;
    private static final int DEFAULT_PROJ_SELECTION = 0;
    
    Color color = new Color(Display.getCurrent(), 240, 240, 240);

    public ExecuteAnonymousViewComposite(Composite parent, int style,
            ExecuteAnonymousController executeAnonymousController) {
        super(parent, style);
        this.executeAnonymousController = executeAnonymousController;
        
        addDisposeListener(new DisposeListener(){
			public void widgetDisposed(DisposeEvent e) {
				color.dispose();
			}
        });
        initialize();
    }

    public void enableComposite(boolean enable) {
        if (txtSourceInput != null) {
            txtSourceInput.setEnabled(enable);
        }

        if (loggingComposite != null) {
            loggingComposite.enable(enable);
        }
    }

    protected void initialize() {
        GridLayout gridLayout = new GridLayout();
        gridLayout.horizontalSpacing = 5;
        gridLayout.numColumns = 2;
        setLayout(gridLayout);
        setSize(new Point(566, 757));

        GridData gridData3 = new GridData();
        gridData3.horizontalAlignment = GridData.FILL;
        gridData3.grabExcessHorizontalSpace = true;
        gridData3.grabExcessVerticalSpace = true;
        gridData3.horizontalSpan = 2;
        gridData3.heightHint = 650;
        gridData3.verticalAlignment = GridData.FILL;
        sashForm = new SashForm(this, SWT.NONE);
        sashForm.setOrientation(SWT.VERTICAL);
        sashForm.setLayoutData(gridData3);

        createSourceComposite();
        createResultComposite();

        loadProjects();
        setActiveProject(executeAnonymousController.getProject());
    }

    protected void createSourceComposite() {
        cmpSource = new Composite(sashForm, SWT.NONE);
        cmpSource.setLayout(new GridLayout(3, false));

        createProjectComposite(cmpSource);
        loggingComposite =
                new LoggingComposite(cmpSource, ContainerDelegate.getInstance().getServiceLocator().getLoggingService(), SWT.NONE, false,
                        LoggingInfo.SupportedFeatureEnum.ExecuteAnonymous);

        // Source to execute: label
        CLabel lblSource = new CLabel(cmpSource, SWT.NONE);
        lblSource.setText("Source to execute:");
        lblSource.setLayoutData(new GridData(GridData.CENTER));

        // Apex input text field        
        txtSourceInput = new StyledText(cmpSource, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        txtSourceInput.setEnabled(false);
        txtSourceInput.setLayoutData(getInputResultsGridData());
        txtSourceInput.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                if (txtSourceInput != null && btnExecute != null) {
                    btnExecute.setEnabled(Utils.isNotEmpty(txtSourceInput.getText()));
                }
            }
        });

        // Execute Anonymous button
        btnExecute = new Button(cmpSource, SWT.NONE);
        btnExecute.setText("Execute Anonymous");
        btnExecute.setEnabled(false);
        btnExecute.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        btnExecute.addMouseListener(new org.eclipse.swt.events.MouseAdapter() {
            @Override
            public void mouseUp(org.eclipse.swt.events.MouseEvent e) {
                executeExecuteAnonymous();
            }
        });
    }

    private GridData getInputResultsGridData() {
        Rectangle rect = UIUtils.getClientArea(getShell());

        GridData gridData = new GridData();
        gridData.horizontalAlignment = GridData.FILL;
        gridData.grabExcessHorizontalSpace = true;
        gridData.grabExcessVerticalSpace = true;
        gridData.horizontalSpan = 3;
        gridData.heightHint = (int) (rect.height * .4);
        gridData.verticalAlignment = GridData.FILL;
        return gridData;
    }

    public void executeExecuteAnonymous() {
        if (executeAnonymousController.getProject() == null) {
            Utils.openError("No Project Selected", "Please select a project from which to execute anonymous.");
            return;
        }

        txtResult.setText("Executing code...");
        txtResult.update();

        final String code = txtSourceInput.getText();
        // Execute the code in a different thread to allow debugging (since DBGP takes up the main thread)
        WorkspaceJob job = new WorkspaceJob("Execute-Anonymous") {
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                ExecuteAnonymousResultExt result = executeAnonymousController.executeExecuteAnonymous(code);
                handleExecuteResults(result);
                return Status.OK_STATUS;
            }
        };
        job.schedule();
    }

    protected void createResultComposite() {
        cmpResult = new Composite(sashForm, SWT.NONE);
        cmpResult.setLayout(new GridLayout(3, false));

        CLabel lblResult = new CLabel(cmpResult, SWT.NONE);
        lblResult.setText("Results:");

        @SuppressWarnings("unused")
        Label filler2 = new Label(cmpResult, SWT.NONE);
        @SuppressWarnings("unused")
        Label filler3 = new Label(cmpResult, SWT.NONE);
        txtResult = new StyledText(cmpResult, SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY | SWT.BORDER);
        txtResult.setBackground(color);
        txtResult.setLayoutData(getInputResultsGridData());
    }

    protected void createProjectComposite(Composite cmpSource) {
        cmpProject = new Composite(cmpSource, SWT.NONE);
        cmpProject.setLayoutData(new GridData(SWT.BEGINNING));
        GridLayout gridLayout1 = new GridLayout();
        gridLayout1.numColumns = 3;
        cmpProject.setLayout(gridLayout1);
        CLabel lblProject = new CLabel(cmpProject, SWT.NONE);
        lblProject.setLayoutData(new GridData(SWT.BEGINNING));
        lblProject.setText("Active Project:");
        cboProject = new Combo(cmpProject, SWT.DROP_DOWN | SWT.READ_ONLY);
        cboProject.addSelectionListener(new org.eclipse.swt.events.SelectionListener() {
            @SuppressWarnings("unchecked")
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                Combo tmpCboProject = (Combo) e.widget;
                if (tmpCboProject.getData() != null && tmpCboProject.getData() instanceof List) {
                    List<IProject> projects = (List<IProject>) tmpCboProject.getData();
                    if (Utils.isNotEmpty(projects)) {
                        int selectionIndex = ((Combo) e.widget).getSelectionIndex();
                        IProject selectedProject = projects.get(selectionIndex);
                        if (selectedProject != null) {
                            setActiveProject(selectedProject);
                        }

                    }
                }
            }

            public void widgetDefaultSelected(org.eclipse.swt.events.SelectionEvent e) {
                widgetSelected(e);
            }
        });
    }

    public void setActiveProject(IProject project) {
        if (project == null) {
            project = getFirstProject();
        }

        setSelectedProjectCombo(project);

        executeAnonymousController.setProject(project);

        if (loggingComposite != null) {
            loggingComposite.setProject(project);
        }

        if (project != null && project.getName().equals(cboProject.getText())) {
            enableComposite(true);
        } else {
            enableComposite(false);
        }
    }

    @SuppressWarnings("unchecked")
    private IProject getFirstProject() {
        IProject firstProject = null;
        if (cboProject.getData() != null && cboProject.getData() instanceof List) {
            List<IProject> projects = (List<IProject>) cboProject.getData();
            firstProject = Utils.isNotEmpty(projects) ? projects.get(0) : null;
        }
        return firstProject;
    }

    public void loadProjects() {
        if (executeAnonymousController == null || cboProject == null) {
            return;
        }

        List<IProject> projects = executeAnonymousController.getForceProjects();
        if (Utils.isNotEmpty(projects)) {
            cboProject.removeAll();
            cboProject.setData(projects);
            Collections.sort(projects, new Comparator<IProject>() {
                public int compare(IProject o1, IProject o2) {
                    return String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName());
                }
            });

            for (IProject project : projects) {
                cboProject.add(project.getName());
            }
            cboProject.select(DEFAULT_PROJ_SELECTION);
            cboProject.setEnabled(true);
        } else {
            cboProject.removeAll();
            cboProject.setData(null);
            cboProject.setEnabled(false);
            enableComposite(false);
        }

        layout(true, true);
    }

    private void setSelectedProjectCombo(IProject selectedProject) {
        if (cboProject != null && Utils.isNotEmpty(selectedProject)) {
            selectComboContent(selectedProject.getName(), cboProject);
        } else {
            cboProject.select(DEFAULT_PROJ_SELECTION);
        }
    }

    private void handleExecuteResults(final ExecuteAnonymousResultExt executeAnonymousResult) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                if (executeAnonymousResult.getCompiled()) {
                    if (executeAnonymousResult.getSuccess()) {
                        txtResult.setText("Anonymous execution was successful.\n\n");
                        if (executeAnonymousResult.getDebugInfo() != null) {
                            txtResult.setText(txtResult.getText() + executeAnonymousResult.getDebugInfo().getDebugLog());
                        }
                    } else {
                        Utils.openError("Error Occurred", executeAnonymousResult.getExceptionMessage());
                        StringBuffer errorMessage = new StringBuffer("DEBUG LOG\n");
                        if (executeAnonymousResult.getDebugInfo() != null) {
                            errorMessage.append(executeAnonymousResult.getDebugInfo().getDebugLog());
                        }
                        txtResult.setText(errorMessage.toString());
                    }
                } else {
                    StringBuilder strBuilder = new StringBuilder("Compile error at line ");
                    strBuilder.append(executeAnonymousResult.getLine()).append(" column ").append(
                        executeAnonymousResult.getColumn()).append("\n").append(executeAnonymousResult.getCompileProblem());
                    txtResult.setText(strBuilder.toString());
                }
            }
        });
    }

    @Override
    public void validateUserInput() {

    }
    
    
}
