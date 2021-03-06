 
package ru.taximaxim.codekeeper.ui.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import ru.taximaxim.codekeeper.ui.Activator;
import ru.taximaxim.codekeeper.ui.Log;
import ru.taximaxim.codekeeper.ui.pgdbproject.DiffWizard;
import ru.taximaxim.codekeeper.ui.pgdbproject.PgDbProject;

public class Diff extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) {
        PgDbProject proj = OpenProjectUtils.getProject(event);
        Shell shell = HandlerUtil.getActiveShell(event);
        IPreferenceStore prefStore = Activator.getDefault().getPreferenceStore();
        Log.log(Log.LOG_DEBUG, "Diff wizard about to show"); //$NON-NLS-1$
            
        WizardDialog dialog = new WizardDialog(
                shell, new DiffWizard(proj, prefStore));
        dialog.open();
        return null;
    }
}