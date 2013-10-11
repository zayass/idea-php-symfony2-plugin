package fr.adrienbrault.idea.symfony2plugin.config.dic;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import fr.adrienbrault.idea.symfony2plugin.Symfony2Icons;
import fr.adrienbrault.idea.symfony2plugin.config.EventDispatcherSubscriberUtil;
import fr.adrienbrault.idea.symfony2plugin.dic.XmlEventParser;
import fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.service.ServiceXmlParserFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventDispatcherEventReference extends PsiPolyVariantReferenceBase<PsiElement> {

    private String eventName;

    public EventDispatcherEventReference(@NotNull PsiElement element, String eventName) {
        super(element);
        this.eventName = eventName;
    }

    public EventDispatcherEventReference(@NotNull StringLiteralExpression element) {
        super(element);
        this.eventName = element.getContents();
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {

        List<ResolveResult> resolveResults = new ArrayList<ResolveResult>();

        for(PsiElement psiElement: EventDispatcherSubscriberUtil.getEventPsiElements(getElement().getProject(), this.eventName)) {
            resolveResults.add(new PsiElementResolveResult(psiElement));
        }

        return resolveResults.toArray(new ResolveResult[resolveResults.size()]);
    }

    @NotNull
    @Override
    public Object[] getVariants() {

        List<LookupElement> results = new ArrayList<LookupElement>();

        XmlEventParser xmlEventParser = ServiceXmlParserFactory.getInstance(getElement().getProject(), XmlEventParser.class);
        for(EventDispatcherSubscribedEvent event : xmlEventParser.getEvents()) {
            results.add(LookupElementBuilder.create(event.getStringValue()).withTypeText(event.getType(), true).withIcon(Symfony2Icons.EVENT));
        }

        for(EventDispatcherSubscribedEvent event: EventDispatcherSubscriberUtil.getSubscribedEvents(getElement().getProject())) {
            results.add(LookupElementBuilder.create(event.getStringValue()).withTypeText(event.getType(), true).withIcon(Symfony2Icons.EVENT));
        }

        return results.toArray();
    }
}
