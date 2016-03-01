package ru.taximaxim.codekeeper.ui.handlers;

import java.text.MessageFormat;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import ru.taximaxim.codekeeper.ui.Log;
import ru.taximaxim.codekeeper.ui.PgCodekeeperUIException;
import ru.taximaxim.codekeeper.ui.UIConsts;
import ru.taximaxim.codekeeper.ui.UIConsts.EDITOR;
import ru.taximaxim.codekeeper.ui.dialogs.ExceptionNotifier;
import ru.taximaxim.codekeeper.ui.editors.ProjectEditorInput;
import ru.taximaxim.codekeeper.ui.localizations.Messages;


public class OpenEditor extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject proj = OpenProjectUtils.getProject(event).getProject();
        if (proj != null) {
            try {
                IFile file = proj.getFile(UIConsts.FILE.CODEKEEPEREDITOR);
                if (!file.exists())
                    file.create(null, IResource.VIRTUAL, null);
                openEditor(HandlerUtil.getActiveWorkbenchWindow(event).getActivePage(), proj);
            } catch (PgCodekeeperUIException e) {
                ExceptionNotifier.notifyDefault(
                        MessageFormat.format(
                                Messages.OpenEditor_error_open_project_editor, proj.getName()), e);
            } catch (CoreException e) {
                ExceptionNotifier.notifyDefault(Messages.NewProjWizard_error_creating_codekeepereditor_file, e);
            }
        }
        return null;
    }

    public static void openEditor(IWorkbenchPage page, IProject proj)
            throws PgCodekeeperUIException {
        Log.log(Log.LOG_INFO, "Opening editor for project: " + proj.getName()); //$NON-NLS-1$
        if (OpenProjectUtils.checkVersionAndWarn(proj,
                page.getWorkbenchWindow().getShell(), true)) {
            ProjectEditorInput input = new ProjectEditorInput(proj.getName());
            try {
                page.openEditor(input, EDITOR.PROJECT);
            } catch (PartInitException e) {
                throw new PgCodekeeperUIException(MessageFormat.format(
                        Messages.OpenEditor_error_open_project,
                        e.getLocalizedMessage()), e);
            }
        }
    }
}
