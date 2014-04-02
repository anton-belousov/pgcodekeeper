package ru.taximaxim.codekeeper.ui.pgdbproject;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jface.preference.PreferenceStore;

import ru.taximaxim.codekeeper.ui.UIConsts;

public class PgDbProject extends PreferenceStore {

    public enum RepoType {
        SVN("SVN"), GIT("GIT");
        private String repoType;

        private RepoType(String repoType) {
            this.repoType = repoType;
        }
        @Override
        public String toString() {
            return repoType;
        }
    }

    private final String projectDir;

    private final String projectName;

    public PgDbProject(String projectDir) {
        super(new File(projectDir, UIConsts.FILENAME_PROJ_PREF_STORE)
                .getAbsolutePath());

        this.projectDir = projectDir;

        this.projectName = Paths.get(projectDir).getFileName().toString();
    }

//    public RepoType getRepoType() {
//        return null;
//    }

    public String getProjectName() {
        return projectName;
    }

    public String getProjectDir() {
        return projectDir;
    }

    public File getProjectDirFile() {
        return new File(projectDir);
    }

    public Path getProjectPath() {
        return getProjectDirFile().toPath();
    }

    public File getProjectPropsFile() {
        return new File(projectDir, UIConsts.FILENAME_PROJ_PREF_STORE);
    }

    public File getProjectSchemaDir() {
        return new File(projectDir, UIConsts.FILENAME_PROJ_SCHEMA_DIR);
    }
}