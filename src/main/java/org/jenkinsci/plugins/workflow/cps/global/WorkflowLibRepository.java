package org.jenkinsci.plugins.workflow.cps.global;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.jenkinsci.plugins.gitserver.FileBackedHttpGitRepository;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Exposes the workflow libs as a git repository over HTTP.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class WorkflowLibRepository extends FileBackedHttpGitRepository implements RootAction {
    @Inject
    UserDefinedGlobalVariableList globalVariableList;

    public WorkflowLibRepository() {
        super(workspace());
    }

    /**
     * Get the root of the {@link WorkflowLibRepository} git repository on the master.
     *
     * To use the repository for things other than {@link UserDefinedGlobalVariable}s, call this to get the repository,
     * and extend {@link WorkflowLibRepositoryListener} to listen for pushes to the repository in order to respond to
     * those pushes by rebuilding stored classes/scripts, etc.
     *
     * @return A {@link File} pointing to the {@link WorkflowLibRepository} git repository on the master.
     */
    public static File workspace() {
        return new File(Jenkins.get().root, "workflow-libs");
    }

    @Override
    protected void checkPushPermission() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "workflowLibs.git";
    }

    /**
     * Starts a new repository without initial import, since this directory
     * was never unmanaged. This will create a nice empty repo that people
     * can push into, as opposed to one they have to pull from.
     *
     * This prevents a user mistake like JENKINS-25632.
     */
    @Override
    protected void createInitialRepository(Repository r) throws IOException {
        r.create();
    }

    @Override
    public ReceivePack createReceivePack(Repository db) {
        ReceivePack rp = super.createReceivePack(db);

        // TODO: FileBackedHttpGitRepository should accept a collection of listeners, not just one
        final PostReceiveHook base = rp.getPostReceiveHook();
        rp.setPostReceiveHook(new PostReceiveHook() {
            @Override
            public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
                base.onPostReceive(rp,commands);
                for (WorkflowLibRepositoryListener hookListener: ExtensionList.lookup(WorkflowLibRepositoryListener.class)) {
                    hookListener.repositoryUpdated();
                }
            }
        });

        return rp;
    }
}
