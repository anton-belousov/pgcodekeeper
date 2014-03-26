package ru.taximaxim.codekeeper.ui.externalcalls;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.DetachedHeadException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.FileTreeIterator;

import ru.taximaxim.codekeeper.ui.UIConsts;
import ru.taximaxim.codekeeper.ui.pgdbproject.PgDbProject;

public class JGitExec implements IRepoWorker{

    private final String url, user, pass;
    public static final Pattern PATTERN_HTTP_URL = Pattern.compile("http(s)?://.+");
    
    public JGitExec() {
        url = user = pass = null;
    }

    public JGitExec(PgDbProject proj) {
        this(proj.getString(UIConsts.PROJ_PREF_REPO_URL), proj
                .getString(UIConsts.PROJ_PREF_REPO_USER), proj
                .getString(UIConsts.PROJ_PREF_REPO_PASS));
    }

    public JGitExec(String url, String user, String pass) {
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    @Override
    public void repoCheckOut(File dirTo) throws IOException {
        repoCheckOut(dirTo, null);
    }

    @Override
    public void repoCheckOut(File dirTo, String rev) throws IOException {
        CloneCommand cloneCom = new CloneCommand();
        if (PATTERN_HTTP_URL.matcher(url).matches()) {
            cloneCom.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, pass));
        }
        try {
            cloneCom.setURI(url).setDirectory(dirTo).call();
        } catch (GitAPIException e) {
            throw new IllegalStateException ("Exception thrown at JGit clone: " + e.getMessage());
        }
    }

    @Override
    public void repoCommit(File dirFrom, String comment) throws IOException {
        Git git = Git.open(dirFrom);
        try {
            git.commit().setMessage(comment).setAll(true).call();
            for (PushResult pushRes : git.push().call()){
                for (RemoteRefUpdate b : pushRes.getRemoteUpdates()){
                    if (b.getStatus() !=  RemoteRefUpdate.Status.OK && b.getStatus() !=  RemoteRefUpdate.Status.UP_TO_DATE){
                        throw new IllegalStateException ("Exception thrown at JGit commit: status is not ok or up_to_date");
                    }
                }
            }
        } catch (WrongRepositoryStateException | ConcurrentRefUpdateException | UnmergedPathsException | NoHeadException | NoMessageException e) {
            throw new IllegalStateException ("Exception thrown at JGit commit: " + e.getMessage());
        }catch (GitAPIException e){
            throw new IllegalStateException ("Exception thrown at JGit commit: " + e.getMessage());
        }
    }

    @Override
    public void repoRemoveMissingAddNew(File dirIn) throws IOException {
        Git git = Git.open(dirIn);
        try {
            git.add().addFilepattern(".").setUpdate(true).call();
            git.add().addFilepattern(".").call();
        } catch (NoFilepatternException e) {
            throw new IllegalStateException(
                    "Exception thrown at JGit repoRemoveMissingAddNew: " + e.getMessage());
        } catch (GitAPIException e) {
            throw new IllegalStateException(
                    "Exception thrown at JGit repoRemoveMissingAddNew: " + e.getMessage());
        }

    }

    @Override
    public String getRepoMetaFolder() {
        return ".git";
    }

    @Override
    public boolean hasConflicts(File dirIn) throws IOException {
        Git git = Git.open(dirIn);
        IndexDiff id = new IndexDiff(git.getRepository(), "HEAD",
                new FileTreeIterator(git.getRepository()));
        id.diff();
        return !id.getConflicting().isEmpty();
    }

    @Override
    public boolean repoUpdate(File dirIn) throws IOException  {
        try {
            PullResult pr =  Git.open(dirIn).pull().call();
            return pr.getMergeResult().getMergeStatus() == MergeStatus.MERGED || pr.getMergeResult().getMergeStatus() == MergeStatus.ALREADY_UP_TO_DATE;
        } catch (WrongRepositoryStateException | InvalidConfigurationException
                | DetachedHeadException | InvalidRemoteException
                | CanceledException | RefNotFoundException | NoHeadException
                | TransportException e) {
            return false;
        }catch (GitAPIException e){
            return false;
        }
    }

    @Override
    public String repoGetVersion() throws IOException {
        // TODO return Eclipse plugin version
        return "JGit version";
    }
}
