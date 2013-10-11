package fr.adrienbrault.idea.symfony2plugin.templating.path;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.twig.TwigFileType;

import java.util.HashMap;
import java.util.Map;

public class TwigPathContentIterator implements ContentIterator {

    private TwigPath twigPath;
    private Project project;

    private Map<String, PsiFile> results = new HashMap<String, PsiFile>();

    private boolean withPhp = false;
    private boolean withTwig = true;

    public TwigPathContentIterator(Project project, TwigPath twigPath) {
        this.twigPath = twigPath;
        this.project = project;
    }

    public boolean processFile(VirtualFile virtualFile) {

        // @TODO make file types more dynamically like eg js
        if(!this.isProcessable(virtualFile)) {
            return true;
        }

        VirtualFile virtualDirectoryFile = twigPath.getDirectory(this.project);
        if(virtualDirectoryFile == null) {
            return true;
        }

        String templatePath = VfsUtil.getRelativePath(virtualFile, virtualDirectoryFile, '/');
        if(templatePath == null) {
            return true;
        }

        String templateDirectory = null; // xxx:XXX:xxx
        String templateFile = null; // xxx:xxx:XXX

        if (templatePath.contains("/")) {
            int lastDirectorySeparatorIndex = templatePath.lastIndexOf("/");
            templateDirectory = templatePath.substring(0, lastDirectorySeparatorIndex);
            templateFile = templatePath.substring(lastDirectorySeparatorIndex + 1);
        } else {
            templateDirectory = "";
            templateFile = templatePath;
        }

        String namespace = this.twigPath.getNamespace().equals(TwigPathIndex.MAIN) ? "" : this.twigPath.getNamespace();

        String templateFinalName;
        if(this.twigPath.getNamespaceType() == TwigPathIndex.NamespaceType.BUNDLE) {
            templateFinalName = namespace + ":" + templateDirectory + ":" + templateFile;
        } else {
            templateFinalName = namespace + "/" + templateDirectory + "/" + templateFile;

            // remove empty path and check for root (global namespace)
            templateFinalName = templateFinalName.replace("//", "/");
            if(templateFinalName.startsWith("/")) {
                templateFinalName = templateFinalName.substring(1);
            } else {
                templateFinalName = "@" + templateFinalName;
            }
        }


        PsiFile psiFile = PsiManager.getInstance(this.project).findFile(virtualFile);
        this.results.put(templateFinalName, psiFile);

        return true;
    }

    private boolean isProcessable(VirtualFile virtualFile) {

        if(withTwig && virtualFile.getFileType() instanceof TwigFileType) {
            return true;
        }

        if(withPhp && virtualFile.getFileType() instanceof PhpFileType) {
            return true;
        }

        return false;
    }

    public TwigPathContentIterator setWithPhp(boolean withPhp) {
        this.withPhp = withPhp;
        return this;
    }

    public TwigPathContentIterator setWithTwig(boolean withTwig) {
        this.withTwig = withTwig;
        return this;
    }

    public Map<String, PsiFile> getResults() {
        return results;
    }
}
