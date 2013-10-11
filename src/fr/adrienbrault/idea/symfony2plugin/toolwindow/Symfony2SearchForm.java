package fr.adrienbrault.idea.symfony2plugin.toolwindow;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBList;
import com.jetbrains.twig.TwigFile;
import fr.adrienbrault.idea.symfony2plugin.Symfony2ProjectComponent;
import fr.adrienbrault.idea.symfony2plugin.TwigHelper;
import fr.adrienbrault.idea.symfony2plugin.dic.ServiceMap;
import fr.adrienbrault.idea.symfony2plugin.dic.ServiceStringLookupElement;
import fr.adrienbrault.idea.symfony2plugin.dic.XmlServiceParser;
import fr.adrienbrault.idea.symfony2plugin.routing.Route;
import fr.adrienbrault.idea.symfony2plugin.routing.RouteHelper;
import fr.adrienbrault.idea.symfony2plugin.routing.RouteLookupElement;
import fr.adrienbrault.idea.symfony2plugin.templating.TemplateLookupElement;
import fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.service.ServiceXmlParserFactory;
import icons.PhpIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class Symfony2SearchForm {

    private JPanel panel1;
    private JBList list1;
    private JToggleButton toggleService;
    private JToggleButton toggleTemplate;
    private JToggleButton toggleRoute;
    private FilterComponent filterComponent1;

    public DefaultListModel listenModel = new DefaultListModel();
    private Project project;

    public Symfony2SearchForm(Project project) {

        this.project = project;

        this.list1.setModel(this.listenModel);
        this.list1.setCellRenderer(new MyLookupCellRenderer());

        this.toggleTemplate.setIcon(PhpIcons.TwigFileIcon);

        list1.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                JList list = (JList) e.getSource();
                if (e.getClickCount() == 2) {
                    if (list.getSelectedValue() instanceof LookupElement) {
                        selectedItem((LookupElement) list.getSelectedValue());
                    }

                }

            }
        });

        ItemListener listener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                Symfony2SearchForm.this.filterComponent1.filter();
            }
        };

        toggleTemplate.addItemListener(listener);
        toggleRoute.addItemListener(listener);
        toggleService.addItemListener(listener);
    }

    public void selectedItem(LookupElement lookupElement) {
        PsiElement psiElement = null;

        if(lookupElement instanceof ServiceStringLookupElement) {
            ServiceMap serviceMap = ServiceXmlParserFactory.getInstance(this.project, XmlServiceParser.class).getServiceMap();
            if(serviceMap.getMap().containsKey(lookupElement.getLookupString())) {
                PsiElement psiElements[] = PhpElementsUtil.getClassInterfacePsiElements(this.project, serviceMap.getMap().get(lookupElement.getLookupString()));
                if(psiElements.length > 0) {
                    psiElement = psiElements[0];
                }
            }
        }

        if(lookupElement instanceof RouteLookupElement) {
            PsiElement psiElements[] = RouteHelper.getMethods(this.project, lookupElement.getLookupString());
            if(psiElements.length > 0) {
                psiElement = psiElements[0];
            }
        }

        if(lookupElement instanceof TemplateLookupElement) {
            Map<String, PsiFile> twigFilesByName = TwigHelper.getTemplateFilesByName(this.project);
            PsiFile twigFile = twigFilesByName.get(lookupElement.getLookupString());
            if (null != twigFile) {
               psiElement = twigFile;
            }

        }

        if(psiElement != null) {
            navigateToPsiElement(psiElement);
        }

    }

    public JComponent createComponent() {
        return panel1;
    }

    public ArrayList<? extends LookupElement> getItems(String filter) {

        filter = filter.toLowerCase();

        ArrayList<LookupElement> items = new ArrayList<LookupElement>();

        if(this.toggleService.isSelected()) {
            Map<String,String> map = ServiceXmlParserFactory.getInstance(this.project, XmlServiceParser.class).getServiceMap().getMap();
            for( Map.Entry<String, String> entry: map.entrySet() ) {
                if(entry.getKey().toLowerCase().contains(filter)) {
                    items.add(new ServiceStringLookupElement(entry.getKey(), entry.getValue()));
                }
            }
        }

        if(this.toggleRoute.isSelected()) {
            Symfony2ProjectComponent symfony2ProjectComponent = this.project.getComponent(Symfony2ProjectComponent.class);
            Map<String,Route> routes = symfony2ProjectComponent.getRoutes();
            for (Route route : routes.values()) {
                if(route.getName().toLowerCase().contains(filter)) {
                    items.add(new RouteLookupElement(route));
                }

            }
        }

        if(this.toggleTemplate.isSelected()) {
            Map<String, PsiFile> twigFilesByName = TwigHelper.getTemplateFilesByName(this.project);
            for (Map.Entry<String, PsiFile> entry : twigFilesByName.entrySet()) {
                if(entry.getKey().toLowerCase().contains(filter)) {
                    items.add(new TemplateLookupElement(entry.getKey(), entry.getValue()));
                }
            }
        }

        return items;
    }

    public void loadSymfonyElements(final String filter) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                final ArrayList<? extends LookupElement> items = getItems(filter);

                DefaultListModel listModel = (DefaultListModel) list1.getModel();
                listModel.removeAllElements();

                ArrayList<SortableLookupItem> sortableLookupItems = new ArrayList<SortableLookupItem>();

                for(LookupElement item: items) {
                    sortableLookupItems.add(new SortableLookupItem(item));
                }

                Collections.sort(sortableLookupItems);

                for(SortableLookupItem sortableLookupItem: sortableLookupItems) {
                    listModel.addElement(sortableLookupItem.getLookupElement());
                }

                Symfony2SearchForm.this.list1.setPaintBusy(false);
                Symfony2SearchForm.this.list1.updateUI();
            }
        }, ModalityState.any());


    }

    private class SymfonyFilterComponents extends FilterComponent {

        public SymfonyFilterComponents() {
            super("bla", 5);
        }

        public void filter() {
            Symfony2SearchForm.this.list1.setPaintBusy(true);
            ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
                public void run() {
                    Symfony2SearchForm.this.loadSymfonyElements(getFilter());
                }
            });
        }
    }

    private void createUIComponents() {
        this.filterComponent1 = new SymfonyFilterComponents();
    }

    /**
     * Pls implement already ready com.intellij.codeInsight.lookup.impl.LookupCellRenderer!
     */
    private class MyLookupCellRenderer extends SimpleColoredComponent implements ListCellRenderer {
        protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            JLabel renderer = (JLabel) defaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if(value instanceof LookupElement) {
                LookupElementPresentation lookupElementPresentation = new LookupElementPresentation();
                ((LookupElement) value).renderElement(lookupElementPresentation);

                renderer.setText(lookupElementPresentation.getItemText());
                renderer.setIcon(lookupElementPresentation.getIcon());
                renderer.setToolTipText(lookupElementPresentation.getTypeText());
            }

            return renderer;
        }
    }

    public static void navigateToPsiElement(PsiElement psiElement) {
        Project project = psiElement.getProject();
        PsiElement navElement = psiElement.getNavigationElement();
        navElement = TargetElementUtilBase.getInstance().getGotoDeclarationTarget(psiElement, navElement);
        if (navElement instanceof Navigatable) {
            if (((Navigatable)navElement).canNavigate()) {
                ((Navigatable)navElement).navigate(true);
            }
        }  else if (navElement != null) {
            int navOffset = navElement.getTextOffset();
            VirtualFile virtualFile = PsiUtilCore.getVirtualFile(navElement);
            if (virtualFile != null) {
                new OpenFileDescriptor(project, virtualFile, navOffset).navigate(true);
            }
        }
    }

    private class SortableLookupItem implements Comparable<SortableLookupItem>  {

        private LookupElement lookupElement;

        public SortableLookupItem(LookupElement lookupElement) {
            this.lookupElement = lookupElement;
        }

        public LookupElement getLookupElement() {
            return lookupElement;
        }

        @Override
        public int compareTo(SortableLookupItem o) {
            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.SECONDARY);
            return collator.compare(this.lookupElement.getLookupString(), o.getLookupElement().getLookupString());
        }

    }

}
