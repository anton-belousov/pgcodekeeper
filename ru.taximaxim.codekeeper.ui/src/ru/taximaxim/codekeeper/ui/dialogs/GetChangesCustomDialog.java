package ru.taximaxim.codekeeper.ui.dialogs;

import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import ru.taximaxim.codekeeper.ui.UIConsts.PREF;
import ru.taximaxim.codekeeper.ui.UIConsts.PROJ_PREF;
import ru.taximaxim.codekeeper.ui.localizations.Messages;

public class GetChangesCustomDialog extends Dialog {

    private Button btnNoPrivileges;
    private Button btnEnableFuncDep;
    private Button btnSimplifyView;
    private Button btnUseGlobalIgnoreList;

    private final IEclipsePreferences projPS;
    private final boolean isMsSql;

    private final Map<String, Boolean> customSettings;

    public GetChangesCustomDialog(Shell parentShell, IEclipsePreferences projPS,
            boolean isMsSql, Map<String, Boolean> customSettings) {
        super(parentShell);
        this.customSettings = customSettings;
        this.projPS = projPS;
        this.isMsSql = isMsSql;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(Messages.DiffTableViewer_get_changes_custom);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite panel = new Composite(parent, SWT.NONE);
        panel.setLayout(new GridLayout(2, false));
        panel.setLayoutData(new GridData(GridData.FILL_BOTH));

        new Label(panel, SWT.NONE).setText(Messages.getChangesCustomDialog_custom_prefs_description);

        btnNoPrivileges = new Button(panel, SWT.CHECK);
        btnNoPrivileges.setText(Messages.dbUpdatePrefPage_ignore_privileges);
        GridData gd = new GridData(SWT.BEGINNING, SWT.DEFAULT, false, false, 2, 1);
        gd.horizontalIndent = 10;
        btnNoPrivileges.setLayoutData(gd);
        btnNoPrivileges.setSelection(projPS.getBoolean(PREF.NO_PRIVILEGES, false));

        //        btnEnableFuncDep = new Button(panel, SWT.CHECK);
        //        btnEnableFuncDep.setText(Messages.GeneralPrefPage_enable_body_dependencies);
        //        btnEnableFuncDep.setToolTipText(Messages.GeneralPrefPage_body_depcy_tooltip);
        //        gd = new GridData(SWT.BEGINNING, SWT.DEFAULT, false, false, 2, 1);
        //        gd.horizontalIndent = 10;
        //        btnEnableFuncDep.setLayoutData(gd);
        //        btnEnableFuncDep.setSelection(prefs.getBoolean(PREF.ENABLE_BODY_DEPENDENCIES, false));
        //
        //        if (!isMsSql) {
        //            btnSimplifyView = new Button(panel, SWT.CHECK);
        //            btnSimplifyView.setText(Messages.GeneralPrefPage_simplify_view);
        //            gd = new GridData(SWT.BEGINNING, SWT.DEFAULT, false, false, 2, 1);
        //            gd.horizontalIndent = 10;
        //            btnSimplifyView.setLayoutData(gd);
        //            btnSimplifyView.setSelection(prefs.getBoolean(PREF.SIMPLIFY_VIEW, false));
        //        }
        //
        //        btnUseGlobalIgnoreList = new Button(panel, SWT.CHECK);
        //        btnUseGlobalIgnoreList.setText(Messages.ProjectProperties_use_global_ignore_list);
        //        gd = new GridData(SWT.BEGINNING, SWT.DEFAULT, false, false, 2, 1);
        //        gd.horizontalIndent = 10;
        //        btnUseGlobalIgnoreList.setLayoutData(gd);
        //        btnUseGlobalIgnoreList.setSelection(prefs.getBoolean(PROJ_PREF.USE_GLOBAL_IGNORE_LIST, true));

        return panel;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        Button btnOk = getButton(IDialogConstants.OK_ID);
        btnOk.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, true, false));
        btnOk.setText(Messages.getChangesCustomDialog_run_with_specified_prefs);
    }

    @Override
    protected void okPressed() {
        customSettings.put(PROJ_PREF.ENABLE_PROJ_PREF_ROOT, true);
        customSettings.put(PREF.NO_PRIVILEGES, btnNoPrivileges.getSelection());
        super.okPressed();

    }

    @Override
    protected boolean isResizable() {
        return true;
    }
}
