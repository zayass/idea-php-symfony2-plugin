package fr.adrienbrault.idea.symfony2plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.twig.*;
import fr.adrienbrault.idea.symfony2plugin.asset.dic.AssetDirectoryReader;
import fr.adrienbrault.idea.symfony2plugin.asset.dic.AssetFile;
import fr.adrienbrault.idea.symfony2plugin.templating.path.*;
import fr.adrienbrault.idea.symfony2plugin.util.SymfonyBundleUtil;
import fr.adrienbrault.idea.symfony2plugin.util.dict.SymfonyBundle;
import fr.adrienbrault.idea.symfony2plugin.util.service.ServiceXmlParserFactory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Adrien Brault <adrien.brault@gmail.com>
 */
public class TwigHelper {

    synchronized public static Map<String, PsiFile> getTemplateFilesByName(Project project, boolean useTwig, boolean usePhp) {
        Map<String, PsiFile> results = new HashMap<String, PsiFile>();
        ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);

        ArrayList<TwigPath> twigPaths = new ArrayList<TwigPath>();
        twigPaths.addAll(getTwigNamespaces(project));

        for (TwigPath twigPath : twigPaths) {
            if(twigPath.isEnabled()) {
                VirtualFile virtualDirectoryFile = twigPath.getDirectory(project);
                if(virtualDirectoryFile != null) {
                    TwigPathContentIterator twigPathContentIterator = new TwigPathContentIterator(project, twigPath).setWithPhp(usePhp).setWithTwig(useTwig);
                    fileIndex.iterateContentUnderDirectory(virtualDirectoryFile, twigPathContentIterator);
                    results.putAll(twigPathContentIterator.getResults());
                }
            }

        }

        return results;
    }

    synchronized public static Map<String, TwigFile> getTwigFilesByName(Project project) {
        Map<String, TwigFile> results = new HashMap<String, TwigFile>();
        for(Map.Entry<String, PsiFile> entry: getTemplateFilesByName(project, true, true).entrySet()) {
            if(entry.getValue() instanceof TwigFile) {
                results.put(entry.getKey(), (TwigFile) entry.getValue());
            }
        }

        return results;
    }

    synchronized public static Map<String, PsiFile> getTemplateFilesByName(Project project) {
        return getTemplateFilesByName(project, true, true);
    }

    @Nullable
    public static TwigNamespaceSetting findManagedTwigNamespace(Project project, TwigPath twigPath) {

        ArrayList<TwigNamespaceSetting> twigNamespaces = (ArrayList<TwigNamespaceSetting>) Settings.getInstance(project).twigNamespaces;
        if(twigNamespaces == null) {
            return null;
        }

        for(TwigNamespaceSetting twigNamespace: twigNamespaces) {
           if(twigNamespace.equals(project, twigPath)) {
                return twigNamespace;
           }
        }

        return null;
    }

    public static PsiElement[] getTemplatePsiElements(Project project, String templateName) {

        // both are valid names first is internal completion
        // @TODO: provide setting for that
        // BarBundle:Foo:steps/step_finish.html.twig
        // BarBundle:Foo/steps:step_finish.html.twig

        if(templateName.matches("^.*?:.*?:.*?/.*?$")) {
            int lastDoublePoint = templateName.lastIndexOf(":");
            String subFolder = templateName.substring(lastDoublePoint + 1, templateName.lastIndexOf("/"));
            String file = templateName.substring(templateName.lastIndexOf("/") + 1);
            templateName = templateName.substring(0, lastDoublePoint) + "/" + subFolder + ":" + file;
        }

        Map<String, PsiFile> twigFiles = TwigHelper.getTemplateFilesByName(project);
        if(!twigFiles.containsKey(templateName)) {
            return new PsiElement[0];
        }

        return new PsiElement[] {twigFiles.get(templateName)};
    }

    synchronized public static ArrayList<TwigPath> getTwigNamespaces(Project project) {
       return getTwigNamespaces(project, true);
    }

    synchronized public static ArrayList<TwigPath> getTwigNamespaces(Project project, boolean includeSettings) {
        ArrayList<TwigPath> twigPaths = new ArrayList<TwigPath>();
        PhpIndex phpIndex = PhpIndex.getInstance(project);

        TwigPathServiceParser twigPathServiceParser = ServiceXmlParserFactory.getInstance(project, TwigPathServiceParser.class);
        twigPaths.addAll(twigPathServiceParser.getTwigPathIndex().getTwigPaths());

        String appDirectoryName = Settings.getInstance(project).directoryToApp;
        VirtualFile globalDirectory = VfsUtil.findRelativeFile(project.getBaseDir(), appDirectoryName, "Resources", "views");
        if(globalDirectory != null) {
            twigPaths.add(new TwigPath(globalDirectory.getPath(), TwigPathIndex.MAIN, TwigPathIndex.NamespaceType.BUNDLE));
        }

        Collection<SymfonyBundle> symfonyBundles = new SymfonyBundleUtil(phpIndex).getBundles();
        for (SymfonyBundle bundle : symfonyBundles) {
            PsiDirectory views = bundle.getSubDirectory("Resources", "views");
            if(views != null) {
                twigPaths.add(new TwigPath(views.getVirtualFile().getPath(), bundle.getName(), TwigPathIndex.NamespaceType.BUNDLE));
            }
        }

        for(TwigPath twigPath: twigPaths) {
            TwigNamespaceSetting twigNamespaceSetting = findManagedTwigNamespace(project, twigPath);
            if(twigNamespaceSetting != null) {
                twigPath.setEnabled(false);
            }
        }

        if(!includeSettings) {
            return twigPaths;
        }

        ArrayList<TwigNamespaceSetting> twigNamespaceSettings = (ArrayList<TwigNamespaceSetting>) Settings.getInstance(project).twigNamespaces;
        if(twigNamespaceSettings != null) {
            for(TwigNamespaceSetting twigNamespaceSetting: twigNamespaceSettings) {
                if(twigNamespaceSetting.isCustom()) {
                    twigPaths.add(new TwigPath(twigNamespaceSetting.getPath(), twigNamespaceSetting.getNamespace(), twigNamespaceSetting.getNamespaceType(), true).setEnabled(twigNamespaceSetting.isEnabled()));

                }
            }
        }

        return twigPaths;
    }


    @Nullable
    public static String getTwigMethodString(@Nullable PsiElement transPsiElement) {
        if (transPsiElement == null) return null;

        ElementPattern<PsiElement> pattern = PlatformPatterns.psiElement(TwigTokenTypes.RBRACE);

        String currentText = transPsiElement.getText();
        for (PsiElement child = transPsiElement.getNextSibling(); child != null; child = child.getNextSibling()) {
            currentText = currentText + child.getText();
            if (pattern.accepts(child)) {
                //noinspection unchecked
                return currentText;
            }
        }

        return null;
    }

    /**
     * Check for {{ include('|')  }}
     *
     * @param functionName twig function name
     */
    public static ElementPattern<PsiElement> getPrintBlockFunctionPattern(String functionName) {
        return PlatformPatterns
            .psiElement(TwigTokenTypes.STRING_TEXT)
            .withParent(
                PlatformPatterns.psiElement(getDeprecatedPrintBlock())
            )
            .afterLeafSkipping(
                PlatformPatterns.or(
                    PlatformPatterns.psiElement(TwigTokenTypes.LBRACE),
                    PlatformPatterns.psiElement(PsiWhiteSpace.class),
                    PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE),
                    PlatformPatterns.psiElement(TwigTokenTypes.SINGLE_QUOTE),
                    PlatformPatterns.psiElement(TwigTokenTypes.DOUBLE_QUOTE)
                ),
                PlatformPatterns.psiElement(TwigTokenTypes.IDENTIFIER).withText(functionName)
            )
            .withLanguage(TwigLanguage.INSTANCE);
    }
    public static ElementPattern<PsiElement> getPrintBlockFunctionPattern() {
        return  PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(getDeprecatedPrintBlock())).withLanguage(TwigLanguage.INSTANCE);
    }

    public static ElementPattern<PsiElement> getBlockTagPattern() {
        return PlatformPatterns
            .psiElement(TwigTokenTypes.IDENTIFIER)
            .withParent(
                PlatformPatterns.psiElement(getDeprecatedBlockTag())
            )
            .afterLeafSkipping(
                PlatformPatterns.or(
                    PlatformPatterns.psiElement(TwigTokenTypes.LBRACE),
                    PlatformPatterns.psiElement(PsiWhiteSpace.class),
                    PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE),
                    PlatformPatterns.psiElement(TwigTokenTypes.SINGLE_QUOTE),
                    PlatformPatterns.psiElement(TwigTokenTypes.DOUBLE_QUOTE)
                ),
                PlatformPatterns.psiElement(TwigTokenTypes.TAG_NAME).withText("block")
            )
            .withLanguage(TwigLanguage.INSTANCE);
    }

    public static ElementPattern<PsiElement> getTransDefaultDomain() {

        return PlatformPatterns.or(
            PlatformPatterns
            .psiElement(TwigTokenTypes.IDENTIFIER)
            .withParent(
                PlatformPatterns.psiElement(getDeprecatedTwigTagWithFileReference())
            )
            .afterLeafSkipping(
                PlatformPatterns.or(
                    PlatformPatterns.psiElement(TwigTokenTypes.LBRACE),
                    PlatformPatterns.psiElement(PsiWhiteSpace.class),
                    PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE),
                    PlatformPatterns.psiElement(TwigTokenTypes.SINGLE_QUOTE),
                    PlatformPatterns.psiElement(TwigTokenTypes.DOUBLE_QUOTE)
                ),
                PlatformPatterns.psiElement(TwigTokenTypes.TAG_NAME).withText("trans_default_domain")
            ).withLanguage(TwigLanguage.INSTANCE),
            PlatformPatterns.psiElement(TwigTokenTypes.STRING_TEXT)
            .withParent(
                PlatformPatterns.psiElement(getDeprecatedTwigTagWithFileReference())
            )
                .afterLeafSkipping(
                    PlatformPatterns.or(
                        PlatformPatterns.psiElement(TwigTokenTypes.LBRACE),
                        PlatformPatterns.psiElement(PsiWhiteSpace.class),
                        PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE),
                        PlatformPatterns.psiElement(TwigTokenTypes.SINGLE_QUOTE),
                        PlatformPatterns.psiElement(TwigTokenTypes.DOUBLE_QUOTE)
                    ),
                    PlatformPatterns.psiElement(TwigTokenTypes.TAG_NAME).withText("trans_default_domain")
                ).withLanguage(TwigLanguage.INSTANCE)
        );
    }

    /**
     * match 'dddd') on ending
     */
    public static ElementPattern<PsiElement> getTransDomainPattern() {
        return PlatformPatterns
            .psiElement(TwigTokenTypes.STRING_TEXT)
            .beforeLeafSkipping(
                PlatformPatterns.or(
                    PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE),
                    PlatformPatterns.psiElement(TwigTokenTypes.SINGLE_QUOTE),
                    PlatformPatterns.psiElement(TwigTokenTypes.DOUBLE_QUOTE)
                ),
                PlatformPatterns.psiElement(TwigTokenTypes.RBRACE)
            )
            .withParent(PlatformPatterns
                .psiElement(getDeprecatedPrintBlock())
            )
            .withLanguage(TwigLanguage.INSTANCE);
    }

    public static ElementPattern<PsiElement> getAutocompletableRoutePattern() {
        return PlatformPatterns
            .psiElement(TwigTokenTypes.STRING_TEXT)
            .afterLeafSkipping(
                PlatformPatterns.or(
                    PlatformPatterns.psiElement(TwigTokenTypes.LBRACE),
                    PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE),
                    PlatformPatterns.psiElement(TwigTokenTypes.SINGLE_QUOTE),
                    PlatformPatterns.psiElement(TwigTokenTypes.DOUBLE_QUOTE)
                ),
                PlatformPatterns.or(
                    PlatformPatterns.psiElement(TwigTokenTypes.IDENTIFIER).withText("path"),
                    PlatformPatterns.psiElement(TwigTokenTypes.IDENTIFIER).withText("url")
                )
            )
            .withLanguage(TwigLanguage.INSTANCE)
        ;
    }

    public static ElementPattern<PsiElement> getAutocompletableAssetPattern() {
        return PlatformPatterns
            .psiElement(TwigTokenTypes.STRING_TEXT)
            .afterLeafSkipping(
                PlatformPatterns.or(
                    PlatformPatterns.psiElement(TwigTokenTypes.LBRACE),
                    PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE),
                    PlatformPatterns.psiElement(TwigTokenTypes.SINGLE_QUOTE),
                    PlatformPatterns.psiElement(TwigTokenTypes.DOUBLE_QUOTE)
                ),
                PlatformPatterns.psiElement(TwigTokenTypes.IDENTIFIER).withText("asset")
            )
            .withLanguage(TwigLanguage.INSTANCE)
        ;
    }

    public static ElementPattern<PsiElement> getTranslationPattern(String... type) {
        return
            PlatformPatterns
                .psiElement(TwigTokenTypes.STRING_TEXT)
                .beforeLeafSkipping(
                    PlatformPatterns.or(
                        PlatformPatterns.psiElement(TwigTokenTypes.LBRACE),
                        PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE),
                        PlatformPatterns.psiElement(TwigTokenTypes.SINGLE_QUOTE),
                        PlatformPatterns.psiElement(TwigTokenTypes.DOUBLE_QUOTE),
                        PlatformPatterns.psiElement(TwigTokenTypes.FILTER),
                        PlatformPatterns.psiElement(TwigTokenTypes.NUMBER)
                    ),
                    PlatformPatterns.psiElement(TwigTokenTypes.IDENTIFIER).withText(
                        PlatformPatterns.string().oneOf(type)
                    )
                )
                .withLanguage(TwigLanguage.INSTANCE);
    }

    public static ElementPattern<PsiElement> getAutocompletableFilterPattern() {
        return
            PlatformPatterns
                .psiElement()
                .afterSibling(PlatformPatterns.psiElement(TwigTokenTypes.FILTER))
                .withLanguage(TwigLanguage.INSTANCE);
    }

    public static ElementPattern<PsiElement> getAutocompletableAssetTag(String tagName) {

        // @TODO: withChild is not working so we are filtering on text

        // pattern to match '..foo.css' but not match eg ='...'
        //
        // {% stylesheets filter='cssrewrite'
        //  'assets/css/foo.css'
        return PlatformPatterns
            .psiElement(TwigTokenTypes.STRING_TEXT)
                .afterLeafSkipping(
                    PlatformPatterns.or(
                        PlatformPatterns.psiElement(TwigTokenTypes.SINGLE_QUOTE),
                        PlatformPatterns.psiElement(TwigTokenTypes.DOUBLE_QUOTE)
                    ),
                    PlatformPatterns.psiElement(PsiWhiteSpace.class)
                )
            .withParent(PlatformPatterns
                .psiElement(getDeprecatedTwigTagWithFileReference())
                .withText(PlatformPatterns.string().startsWith("{% " + tagName))
            );
    }
    public static ElementPattern<PsiElement> getTemplateFileReferenceTagPattern() {

        // {% include '<xxx>' with {'foo' : bar, 'bar' : 'foo'} %}
        return PlatformPatterns
            .psiElement(TwigTokenTypes.STRING_TEXT)
            .afterLeafSkipping(
                PlatformPatterns.or(
                    PlatformPatterns.psiElement(TwigTokenTypes.LBRACE),
                    PlatformPatterns.psiElement(PsiWhiteSpace.class),
                    PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE),
                    PlatformPatterns.psiElement(TwigTokenTypes.SINGLE_QUOTE),
                    PlatformPatterns.psiElement(TwigTokenTypes.DOUBLE_QUOTE)
                ),
                PlatformPatterns.psiElement(TwigTokenTypes.TAG_NAME).withText(PlatformPatterns.string().oneOf("extends", "from", "include", "use", "import", "embed"))
            )
            .withLanguage(TwigLanguage.INSTANCE);
    }

    public static ElementPattern<PsiElement> getTemplateImportFileReferenceTagPattern() {


        // @TODO: first pattern need some more filter; also 'from' tag would be nice
        // first: {% from '<xxx>' import foo, <|>  %}
        // second: {% from '<xxx>' import <|>  %}
        return
            PlatformPatterns.or(
                PlatformPatterns
                    .psiElement(TwigTokenTypes.IDENTIFIER)
                    .withParent(getDeprecatedTwigTagWithFileReference())
                    .afterLeafSkipping(
                        PlatformPatterns.or(
                            PlatformPatterns.psiElement(PsiWhiteSpace.class),
                            PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE),
                            PlatformPatterns.psiElement(TwigTokenTypes.IDENTIFIER).withoutText("import"),
                            PlatformPatterns.psiElement(TwigTokenTypes.COMMA)
                        ),
                        PlatformPatterns.psiElement(TwigTokenTypes.IDENTIFIER).withText(
                            PlatformPatterns.string().oneOf("import")
                        )
                    )
                    .withLanguage(TwigLanguage.INSTANCE),
                PlatformPatterns
                    .psiElement(TwigTokenTypes.IDENTIFIER)
                    .withParent(getDeprecatedTwigTagWithFileReference())
                    .afterLeafSkipping(
                        PlatformPatterns.or(
                            PlatformPatterns.psiElement(PsiWhiteSpace.class),
                            PlatformPatterns.psiElement(TwigTokenTypes.WHITE_SPACE)
                        ),
                        PlatformPatterns.psiElement(TwigTokenTypes.IDENTIFIER).withText(
                            PlatformPatterns.string().oneOf("import")
                        )
                    )
                    .withLanguage(TwigLanguage.INSTANCE)
            );
    }


    @Nullable
    public static IElementType getNamedElementType(String className, String fieldName) {
        IElementType initClass = null;
        try {
            Class c = Class.forName(className);
            initClass = (IElementType) c.getDeclaredField(fieldName).get(c.getDeclaredField(fieldName));

        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException: " + className + "." + fieldName);
        } catch (NoSuchFieldException e) {
            System.out.println("NoSuchFieldException: " + className + "." + fieldName);
        } catch (IllegalAccessException e) {
            System.out.println("IllegalAccessException: " + className + "." + fieldName);
        }

        return initClass;
    }

    @Nullable
    public static Class getDeprecatedTwigTagWithFileReference() {
        Class c = null;
        try {
            c = Class.forName("com.jetbrains.twig.TwigTagWithFileReference");
            return c;
        } catch (ClassNotFoundException ignored) {
        }

        try {
            c = Class.forName("com.jetbrains.twig.elements.TwigTagWithFileReference");
            return c;
        } catch (ClassNotFoundException ignored) {
        }


        throw  new IllegalArgumentException("com.jetbrains.twig.elements.TwigTagWithFileReference");

    }


    @Nullable
    public static IElementType getDeprecatedBlockTag() {
        IElementType iElementType = getNamedElementType("com.jetbrains.twig.TwigCompositeElementTypes",  "BLOCK_TAG");
        if(iElementType != null) {
            return iElementType;
        }

        iElementType = getNamedElementType("com.jetbrains.twig.elements.TwigElementTypes",  "BLOCK_TAG");
        if(iElementType != null) {
            return iElementType;
        }

        throw new IllegalArgumentException("no BLOCK_TAG found");

    }

    @Nullable
    public static IElementType getDeprecatedPrintBlock() {
        IElementType iElementType = getNamedElementType("com.jetbrains.twig.TwigCompositeElementTypes",  "PRINT_BLOCK");
        if(iElementType != null) {
            return iElementType;
        }

        iElementType = getNamedElementType("com.jetbrains.twig.elements.TwigElementTypes",  "PRINT_BLOCK");
        if(iElementType != null) {
            return iElementType;
        }

        throw new IllegalArgumentException("no PRINT_BLOCK found");
    }

    @Nullable
    public static IElementType getDeprecatedMacroTag() {
        IElementType iElementType = getNamedElementType("com.jetbrains.twig.TwigCompositeElementTypes",  "MACRO_TAG");
        if(iElementType != null) {
            return iElementType;
        }

        iElementType = getNamedElementType("com.jetbrains.twig.elements.TwigElementTypes",  "MACRO_TAG");
        if(iElementType != null) {
            return iElementType;
        }

        throw new IllegalArgumentException("no MACRO_TAG found");
    }

    public static ArrayList<VirtualFile> resolveAssetsFiles(Project project, String templateName, String... fileTypes) {


        ArrayList<VirtualFile> virtualFiles = new ArrayList<VirtualFile>();

        // {% javascripts '@SampleBundle/Resources/public/js/*' %}
        // {% javascripts 'assets/js/*' %}
        // {% javascripts 'assets/js/*.js' %}
        Matcher matcher = Pattern.compile("^(.*[/\\\\])\\*([.\\w+]*)$").matcher(templateName);
        if (!matcher.find()) {

            for (final AssetFile assetFile : new AssetDirectoryReader().setFilterExtension(fileTypes).setIncludeBundleDir(true).setProject(project).getAssetFiles()) {
                if(assetFile.toString().equals(templateName)) {
                    virtualFiles.add(assetFile.getFile());
                }
            }

            return virtualFiles;
        }

        String pathName = matcher.group(1);
        String fileExtension = matcher.group(2).length() > 0 ? matcher.group(2) : null;

        for (final AssetFile assetFile : new AssetDirectoryReader().setFilterExtension(fileTypes).setIncludeBundleDir(true).setProject(project).getAssetFiles()) {
            if(fileExtension == null && assetFile.toString().matches(Pattern.quote(pathName) + "(?!.*[/\\\\]).*\\.\\w+")) {
                virtualFiles.add(assetFile.getFile());
            } else if(fileExtension != null && assetFile.toString().matches(Pattern.quote(pathName) + "(?!.*[/\\\\]).*" + Pattern.quote(fileExtension))) {
                virtualFiles.add(assetFile.getFile());
            }
        }

        return virtualFiles;
    }

}
