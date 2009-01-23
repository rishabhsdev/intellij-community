/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author max
 */
public class MethodUsagesSearcher implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {
  public boolean execute(final MethodReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    final PsiMethod method = p.getMethod();
    SearchScope searchScope = p.getScope();
    PsiManager psiManager = PsiManager.getInstance(method.getProject());
    final boolean isStrictSignatureSearch = p.isStrictSignatureSearch();

    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return true;

    if (method.isConstructor()) {
      final ConstructorReferencesSearchHelper helper = new ConstructorReferencesSearchHelper(psiManager);
      if (!helper.processConstructorReferences(consumer, method, searchScope, !isStrictSignatureSearch, isStrictSignatureSearch)) {
        return false;
      }
    }

    final PsiClass parentClass = method.getContainingClass();
    boolean needStrictSignatureSearch = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return isStrictSignatureSearch && (parentClass == null
                                        || parentClass instanceof PsiAnonymousClass
                                        || parentClass.hasModifierProperty(PsiModifier.FINAL)
                                        || method.hasModifierProperty(PsiModifier.STATIC)
                                        || method.hasModifierProperty(PsiModifier.FINAL)
                                        || method.hasModifierProperty(PsiModifier.PRIVATE));
      }
    }).booleanValue();
    if (needStrictSignatureSearch) {
      return ReferencesSearch.search(method, searchScope, false).forEach(new ReadActionProcessor<PsiReference>() {
        public boolean processInReadAction(final PsiReference psiReference) {
          return consumer.process(psiReference);
        }
      });
    }

    final String text = method.getName();
    final PsiMethod[] methods = isStrictSignatureSearch ? new PsiMethod[]{method} : getOverloads(method);

    SearchScope accessScope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      public SearchScope compute() {
        SearchScope accessScope = methods[0].getUseScope();
        for (int i = 1; i < methods.length; i++) {
          PsiMethod method1 = methods[i];
          SearchScope someScope = PsiSearchScopeUtil.scopesUnion(accessScope, method1.getUseScope());
          accessScope = someScope == null ? accessScope : someScope;
        }
        return accessScope;
      }
    });

    final TextOccurenceProcessor processor1 = new MethodTextOccurenceProcessor(consumer, aClass, isStrictSignatureSearch, methods);

    searchScope = searchScope.intersectWith(accessScope);

    short searchContext = UsageSearchContext.IN_CODE | UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_FOREIGN_LANGUAGES;
    boolean toContinue = psiManager.getSearchHelper().processElementsWithWord(processor1,
                                                                              searchScope,
                                                                              text,
                                                                              searchContext, true);
    if (!toContinue) return false;

    final String propertyName = ApplicationManager.getApplication().runReadAction(new Computable<String>(){
      public String compute() {
        return PropertyUtil.getPropertyName(method);
      }
    });
    if (propertyName != null) {
      if (searchScope instanceof GlobalSearchScope) {
        GlobalSearchScope restrictedSeachScope = GlobalSearchScope.getScopeRestrictedByFileTypes(
          (GlobalSearchScope)searchScope,
          StdFileTypes.JSP,
          StdFileTypes.JSPX,
          StdFileTypes.XML,
          StdFileTypes.XHTML
        );
        toContinue = psiManager.getSearchHelper().processElementsWithWord(processor1,
                                                                        restrictedSeachScope,
                                                                        propertyName,
                                                                        UsageSearchContext.IN_FOREIGN_LANGUAGES, true);
      }
      else {
        toContinue = psiManager.getSearchHelper().processElementsWithWord(processor1,
                                                                        searchScope,
                                                                        propertyName,
                                                                        UsageSearchContext.IN_FOREIGN_LANGUAGES, true);
      }
      if (!toContinue) return false;

      for (CustomPropertyScopeProvider provider : Extensions.getExtensions(CustomPropertyScopeProvider.EP_NAME)) {
        final SearchScope scope = searchScope.intersectWith(provider.getScope(psiManager.getProject()));
        toContinue = psiManager.getSearchHelper().processElementsWithWord(processor1,
                                                                          scope,
                                                                          propertyName,
                                                                          UsageSearchContext.IN_FOREIGN_LANGUAGES, true);
        if (!toContinue) return false;
      }
    }

    return true;
  }

  private static PsiMethod[] getOverloads(final PsiMethod method) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiMethod[]>() {
      public PsiMethod[] compute() {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) return new PsiMethod[]{method};
        return aClass.findMethodsByName(method.getName(), false);
      }
    });
  }

}
