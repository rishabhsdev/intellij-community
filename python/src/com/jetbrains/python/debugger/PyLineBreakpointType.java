// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.google.common.collect.Sets;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XLineBreakpointTypeBase;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;


public class PyLineBreakpointType extends XLineBreakpointTypeBase {
  public static final String ID = "python-line";

  private static final Set<IElementType> UNSTOPPABLE_ELEMENT_TYPES = Sets.newHashSet(PyTokenTypes.TRIPLE_QUOTED_STRING,
                                                                                     PyTokenTypes.SINGLE_QUOTED_STRING,
                                                                                     PyTokenTypes.SINGLE_QUOTED_UNICODE,
                                                                                     PyTokenTypes.DOCSTRING);

  @SuppressWarnings("unchecked") private static final Class<? extends PsiElement>[] UNSTOPPABLE_ELEMENTS = new Class[]{PsiWhiteSpace.class, PsiComment.class};

  public PyLineBreakpointType() {
    super(ID, PyBundle.message("debugger.line.breakpoint.type"), new PyDebuggerEditorsProvider());
  }

  public PyLineBreakpointType(final @NotNull String id, final @NotNull @Nls String title, @Nullable XDebuggerEditorsProvider editorsProvider) {
    super(id, title, editorsProvider);
  }

  @Override
  public boolean canPutAt(final @NotNull VirtualFile file, final int line, final @NotNull Project project) {
    final Ref<Boolean> stoppable = Ref.create(false);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null && isSuitableFileType(project, file)) {
      lineHasStoppablePsi(project, line, document, getUnstoppableElements(), getUnstoppableElementTypes(), stoppable);
    }

    return stoppable.get();
  }

  protected boolean isSuitableFileType(@NotNull Project project, @NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, getFileType()) ||
           (ScratchUtil.isScratch(file) && LanguageUtil.getLanguageForPsi(project, file) == getFileLanguage());
  }

  protected @NotNull FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  protected Language getFileLanguage() {
    return PythonLanguage.INSTANCE;
  }

  protected Set<IElementType> getUnstoppableElementTypes() {
    return UNSTOPPABLE_ELEMENT_TYPES;
  }

  protected Class<? extends PsiElement>[] getUnstoppableElements() {
    return UNSTOPPABLE_ELEMENTS;
  }

  /**
   * We can't rely only on file type, because there are Cython files which contain
   * Python & Cython elements and there are Jupyter files which contain Python & Markdown elements
   * That's why we should check that there is at least one stoppable psiElement at the line
   *
   * @param psiElement to check
   * @return true if psiElement is compatible with breakpoint type and false otherwise
   */
  protected boolean isPsiElementStoppable(PsiElement psiElement) {
    return psiElement.getLanguage() == PythonLanguage.INSTANCE;
  }

  protected void lineHasStoppablePsi(@NotNull Project project,
                                     int line,
                                     Document document,
                                     Class<? extends PsiElement>[] unstoppablePsiElements,
                                     Set<IElementType> unstoppableElementTypes,
                                     Ref<? super Boolean> stoppable) {
    XDebuggerUtil.getInstance().iterateLine(project, document, line, psiElement -> {
      if (PsiTreeUtil.getNonStrictParentOfType(psiElement, unstoppablePsiElements) != null) return true;
      if (psiElement.getNode() != null && unstoppableElementTypes.contains(psiElement.getNode().getElementType())) return true;
      if (isPsiElementStoppable(psiElement)) {
        stoppable.set(true);
      }
      return false;
    });

    if (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
      stoppable.set(false);
    }
  }

  @Override
  public boolean isSuspendThreadSupported() {
    return true;
  }

  @Override
  public SuspendPolicy getDefaultSuspendPolicy() {
    return SuspendPolicy.THREAD;
  }

  @Override
  public String getBreakpointsDialogHelpTopic() {
    return "reference.dialogs.breakpoints";
  }
}
