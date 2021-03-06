package fr.adrienbrault.idea.symfony2plugin;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Adrien Brault <adrien.brault@gmail.com>
 */
public class Symfony2InterfacesUtil {

    public boolean isContainerGetCall(PsiElement e) {
        return isCallTo(e, new Method[] {
            getInterfaceMethod(e.getProject(), "\\Symfony\\Component\\DependencyInjection\\ContainerInterface", "get"),
            getClassMethod(e.getProject(), "\\Symfony\\Bundle\\FrameworkBundle\\Controller\\Controller", "get"),
        });
    }

    public boolean isContainerGetCall(Method e) {
        return isCallTo(e, new Method[] {
            getInterfaceMethod(e.getProject(), "\\Symfony\\Component\\DependencyInjection\\ContainerInterface", "get"),
            getClassMethod(e.getProject(), "\\Symfony\\Bundle\\FrameworkBundle\\Controller\\Controller", "get"),
        });
    }

    public boolean isContainerParameterCall(PsiElement e) {
        return isCallTo(e, new Method[] {
            getInterfaceMethod(e.getProject(), "\\Symfony\\Component\\DependencyInjection\\ContainerInterface", "hasParameter"),
            getInterfaceMethod(e.getProject(), "\\Symfony\\Component\\DependencyInjection\\ContainerInterface", "getParameter"),
        });
    }

    public boolean isTemplatingRenderCall(PsiElement e) {
        return isCallTo(e, new Method[] {
            getInterfaceMethod(e.getProject(), "\\Symfony\\Component\\Templating\\EngineInterface", "render"),
            getInterfaceMethod(e.getProject(), "\\Symfony\\Component\\Templating\\StreamingEngineInterface", "stream"),
            getInterfaceMethod(e.getProject(), "\\Symfony\\Bundle\\FrameworkBundle\\Templating\\EngineInterface", "renderResponse"),
            getClassMethod(e.getProject(), "\\Symfony\\Bundle\\FrameworkBundle\\Controller\\Controller", "render"),
            getClassMethod(e.getProject(), "\\Symfony\\Bundle\\FrameworkBundle\\Controller\\Controller", "renderView"),
            getClassMethod(e.getProject(), "\\Symfony\\Bundle\\FrameworkBundle\\Controller\\Controller", "stream"),
        });
    }

    public boolean isUrlGeneratorGenerateCall(PsiElement e) {
        return isCallTo(e, new Method[] {
            getInterfaceMethod(e.getProject(), "\\Symfony\\Component\\Routing\\Generator\\UrlGeneratorInterface", "generate"),
            getClassMethod(e.getProject(), "\\Symfony\\Bundle\\FrameworkBundle\\Controller\\Controller", "generateUrl"),
        });
    }

    public boolean isTranslatorCall(PsiElement e) {
        return isCallTo(e, new Method[] {
                getInterfaceMethod(e.getProject(), "\\Symfony\\Component\\Translation\\TranslatorInterface", "trans"),
                getInterfaceMethod(e.getProject(), "\\Symfony\\Component\\Translation\\TranslatorInterface", "transChoice"),
        });
    }

    public boolean isGetRepositoryCall(PsiElement e) {
        return isCallTo(e, new Method[] {
                getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ManagerRegistry", "getRepository"),
                getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ObjectManager", "getRepository"),
        });
    }

    public boolean isGetRepositoryCall(Method e) {
        return isCallTo(e, new Method[] {
            getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ManagerRegistry", "getRepository"),
            getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ObjectManager", "getRepository"),
        });
    }

    public boolean isObjectRepositoryCall(Method e) {
        return isCallTo(e, new Method[] {
            getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ObjectRepository", "find"),
            getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ObjectRepository", "findOneBy"),
            getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ObjectRepository", "findAll"),
            getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ObjectRepository", "findBy"),
        });
    }

    public boolean isObjectRepositoryCall(PsiElement e) {
        return isCallTo(e, new Method[] {
                getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ObjectRepository", "find"),
                getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ObjectRepository", "findOneBy"),
                getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ObjectRepository", "findAll"),
                getInterfaceMethod(e.getProject(), "\\Doctrine\\Common\\Persistence\\ObjectRepository", "findBy"),
        });
    }

    public boolean isFormBuilderFormTypeCall(PsiElement e) {
        return isCallTo(e, new Method[] {
                getInterfaceMethod(e.getProject(), "\\Symfony\\Component\\Form\\FormBuilderInterface", "add"),
                getClassMethod(e.getProject(), "\\Symfony\\Component\\Form\\FormBuilderInterface", "create"),
        });
    }

    protected boolean isCallTo(PsiElement e, Method expectedMethod) {
        return isCallTo(e, new Method[] { expectedMethod });
    }

    protected boolean isCallTo(PsiElement e, Method[] expectedMethods) {
        return isCallTo(e, expectedMethods, 1);
    }

    protected boolean isCallTo(Method e, Method[] expectedMethods) {

        PhpClass methodClass = e.getContainingClass();

        for (Method expectedMethod : Arrays.asList(expectedMethods)) {
            if (null != expectedMethod
                && expectedMethod.getName().equals(e.getName())
                && isInstanceOf(methodClass, expectedMethod.getContainingClass())) {
                return true;
            }
        }

        return false;
    }


    protected boolean isCallTo(PsiElement e, Method[] expectedMethods, int deepness) {
        if (!(e instanceof MethodReference)) {
            return false;
        }

        MethodReference methodRef = (MethodReference) e;

        // resolve is also called on invalid php code like "use <xxx>"
        // so double check the method name before resolve the method
        if(!isMatchingMethodName(methodRef, expectedMethods)) {
            return false;
        }

        PsiReference psiReference = methodRef.getReference();
        if (null == psiReference) {
            return false;
        }

        PsiElement resolvedReference = psiReference.resolve();
        if (!(resolvedReference instanceof Method)) {
            return false;
        }

        Method method = (Method) resolvedReference;
        PhpClass methodClass = method.getContainingClass();

        for (Method expectedMethod : Arrays.asList(expectedMethods)) {
            if (null != expectedMethod
                    && expectedMethod.getName().equals(method.getName())
                    && isInstanceOf(methodClass, expectedMethod.getContainingClass())) {
                return true;
            }
        }

        return false;
    }

    protected boolean isMatchingMethodName(MethodReference methodRef, Method[] expectedMethods) {
        for (Method expectedMethod : Arrays.asList(expectedMethods)) {
            if(expectedMethod != null && expectedMethod.getName().equals(methodRef.getName())) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    public static String getFirstArgumentStringValue(MethodReference e) {
        String stringValue = null;

        PsiElement[] parameters = e.getParameters();
        if (parameters.length > 0 && parameters[0] instanceof StringLiteralExpression) {
            StringLiteralExpression stringLiteralExpression = (StringLiteralExpression)parameters[0];
            stringValue = stringLiteralExpression.getText(); // quoted string
            stringValue = stringValue.substring(stringLiteralExpression.getValueRange().getStartOffset(), stringLiteralExpression.getValueRange().getEndOffset());
        }

        return stringValue;
    }

    protected Method getInterfaceMethod(Project project, String interfaceFQN, String methodName) {
        PhpIndex phpIndex = PhpIndex.getInstance(project);
        Object[] interfaces = phpIndex.getInterfacesByFQN(interfaceFQN).toArray();

        if (interfaces.length < 1) {
            return null;
        }

        return findClassMethodByName((PhpClass)interfaces[0], methodName);
    }

    protected Method getClassMethod(Project project, String classFQN, String methodName) {
        PhpIndex phpIndex = PhpIndex.getInstance(project);
        Object[] classes = phpIndex.getClassesByFQN(classFQN).toArray();

        if (classes.length < 1) {
            return null;
        }

        return findClassMethodByName((PhpClass)classes[0], methodName);
    }

    protected Method findClassMethodByName(PhpClass phpClass, String methodName) {
        for (Method method : phpClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }

        return null;
    }

    protected boolean isImplementationOfInterface(PhpClass phpClass, PhpClass phpInterface) {
        if (phpClass == phpInterface) {
            return true;
        }

        for (PhpClass implementedInterface : phpClass.getImplementedInterfaces()) {
            if (isImplementationOfInterface(implementedInterface, phpInterface)) {
                return true;
            }
        }

        if (null == phpClass.getSuperClass()) {
            return false;
        }

        return isImplementationOfInterface(phpClass.getSuperClass(), phpInterface);
    }

    public boolean isInstanceOf(PhpClass subjectClass, PhpClass expectedClass) {
        if (subjectClass == expectedClass) {
            return true;
        }

        if (expectedClass.isInterface()) {
            return isImplementationOfInterface(subjectClass, expectedClass);
        }

        if (null == subjectClass.getSuperClass()) {
            return false;
        }

        return isInstanceOf(subjectClass.getSuperClass(), expectedClass);
    }

    public boolean isCallTo(PsiElement e, String ClassInterfaceName, String methodName) {

        // we need a full fqn name
        if(ClassInterfaceName.contains("\\") && !ClassInterfaceName.startsWith("\\")) {
            ClassInterfaceName = "\\" + ClassInterfaceName;
        }

        return isCallTo(e, new Method[] {
            getInterfaceMethod(e.getProject(), ClassInterfaceName, methodName),
            getClassMethod(e.getProject(), ClassInterfaceName, methodName),
        });
    }

    public boolean isCallTo(Method e, String ClassInterfaceName, String methodName) {

        // we need a full fqn name
        if(ClassInterfaceName.contains("\\") && !ClassInterfaceName.startsWith("\\")) {
            ClassInterfaceName = "\\" + ClassInterfaceName;
        }

        return isCallTo(e, new Method[] {
            getInterfaceMethod(e.getProject(), ClassInterfaceName, methodName),
            getClassMethod(e.getProject(), ClassInterfaceName, methodName),
        });
    }

}
