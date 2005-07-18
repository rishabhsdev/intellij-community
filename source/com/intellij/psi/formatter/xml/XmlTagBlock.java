package com.intellij.psi.formatter.xml;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.lang.ASTNode;
import com.intellij.formatting.*;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class XmlTagBlock extends AbstractXmlBlock{
  private final Indent myIndent;

  public XmlTagBlock(final ASTNode node,
                     final Wrap wrap,
                     final Alignment alignment,
                     final XmlFormattingPolicy policy,
                     final Indent indent) {
    super(node, wrap, alignment, policy);
    myIndent = indent;
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = myNode.getFirstChildNode();
    final Wrap attrWrap = Wrap.createWrap(getWrapType(myXmlFormattingPolicy.getAttributesWrap()), false);
    final Wrap textWrap = Wrap.createWrap(getWrapType(myXmlFormattingPolicy.getTextWrap()), true);
    final Wrap tagBeginWrap = createTagBeginWrapping(getTag());
    final Alignment attrAlignment = Alignment.createAlignment();
    final Alignment textAlignment = Alignment.createAlignment();
    final ArrayList<Block> result = new ArrayList<Block>();
    ArrayList<Block> localResult = new ArrayList<Block>();

    boolean insideTag = true;

    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){

        Wrap wrap = chooseWrap(child, tagBeginWrap, attrWrap, textWrap);
        Alignment alignment = chooseAlignment(child, attrAlignment, textAlignment);

        if (child.getElementType() == ElementType.XML_TAG_END) {
          child = processChild(localResult,child, wrap, alignment, null);
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<Block>();
          insideTag = true;
        }
        else if (child.getElementType() == ElementType.XML_START_TAG_START) {
          insideTag = false;
          if (!localResult.isEmpty()) {
            result.add(createTagContentNode(localResult));
          }
          localResult = new ArrayList<Block>();
          child = processChild(localResult,child, wrap, alignment, null);
        }
        else if (child.getElementType() == ElementType.XML_END_TAG_START) {
          insideTag = false;
          if (!localResult.isEmpty()) {
            result.add(createTagContentNode(localResult));
            localResult = new ArrayList<Block>();
          }
          child = processChild(localResult,child, wrap, alignment, null);
        } else if (child.getElementType() == ElementType.XML_EMPTY_ELEMENT_END) {
          child = processChild(localResult,child, wrap, alignment, null);
          result.add(createTagDescriptionNode(localResult));
          localResult = new ArrayList<Block>();
        }
        else if (isJspxJavaContainingNode(child)) {
          createJspTextNode(localResult, child, getChildIndent());
        }
        else if (child.getElementType() == ElementType.XML_TEXT) {
          child  = createXmlTextBlocks(localResult, child, wrap, alignment);
        }
        else {
          final Indent indent;

          if (localResult.size() == 1 && localResult.get(0) instanceof JspTextBlock) {
            //indent = FormatterEx.getInstance().getNoneIndent();
            indent = getChildrenIndent();
          } else if (!insideTag) {
            indent = null;
          }
          else {
            indent = getChildrenIndent();
          }

          child = processChild(localResult,child, wrap, alignment, indent);
        }
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }

    if (!localResult.isEmpty()) {
      result.add(createTagContentNode(localResult));
    }

    return result;

  }

  private Indent getChildrenIndent() {
    return myXmlFormattingPolicy.indentChildrenOf(getTag())
           ? Indent.getNormalIndent()
           : Indent.getNoneIndent();
  }

  public Indent getIndent() {
    return myIndent;
  }

  private ASTNode createXmlTextBlocks(final ArrayList<Block> list, final ASTNode textNode, final Wrap wrap, final Alignment alignment) {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = textNode.getFirstChildNode();
    return createXmlTextBlocks(list, textNode, child, wrap, alignment);
  }

  private ASTNode createXmlTextBlocks(final ArrayList<Block> list, final ASTNode textNode, ASTNode child,
                                      final Wrap wrap,
                                      final Alignment alignment
  ) {
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        final Indent indent = getChildrenIndent();
        child = processChild(list,child,  wrap, alignment, indent);
        if (child == null) return child;
        if (child.getTreeParent() != textNode) {
          if (child.getTreeParent() != myNode) {
            return createXmlTextBlocks(list, child.getTreeParent(), child.getTreeNext(), wrap, alignment);
          } else {
            return child;
          }
        }
      }
      child = child.getTreeNext();
    }
    return textNode;
  }

  private Block createTagContentNode(final ArrayList<Block> localResult) {
    return AbstractSyntheticBlock.createSynteticBlock(
      localResult, this, Indent.getNoneIndent(),
      myXmlFormattingPolicy,
     getChildrenIndent());
  }

  private Block createTagDescriptionNode(final ArrayList<Block> localResult) {
    return AbstractSyntheticBlock.createSynteticBlock(
      localResult, this, Indent.getNoneIndent(),
      myXmlFormattingPolicy,
      null);
  }

  public Spacing getSpacing(Block child1, Block child2) {
    final AbstractSyntheticBlock syntheticBlock1 = ((AbstractSyntheticBlock)child1);
    final AbstractSyntheticBlock syntheticBlock2 = ((AbstractSyntheticBlock)child2);

    if (syntheticBlock2.isJspTextBlock()) {
      return Spacing.createSpacing(0, 0, 1, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (myXmlFormattingPolicy.keepWhiteSpacesInsideTag(getTag())) return Spacing.getReadOnlySpacing();

    if (myXmlFormattingPolicy.getShouldKeepWhiteSpaces()) {
      return Spacing.getReadOnlySpacing();
    }

    if (syntheticBlock1.endsWithTextElement() && syntheticBlock2.startsWithTextElement()) {
      return Spacing.createSafeSpacing(myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    }

    if (syntheticBlock1.endsWithText()) { //text</tag
      return Spacing.createSpacing(0, 0, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    } else if (syntheticBlock1.isTagDescription() && syntheticBlock2.isTagDescription()) { //></
      return Spacing.createSpacing(0, 0, 0, myXmlFormattingPolicy.getShouldKeepLineBreaks(), myXmlFormattingPolicy.getKeepBlankLines());
    } else if (syntheticBlock2.startsWithText()) { //>text
      return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
    } else if (syntheticBlock1.isTagDescription() && syntheticBlock2.startsWithTag()) {
      return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
    } else if (syntheticBlock1.endsWithTag() && syntheticBlock2.isTagDescription()) {
      return Spacing.createSpacing(0, 0, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
    }
    else {
      return createDefaultSpace(true);
    }

  }

  public boolean insertLineBreakBeforeTag() {
    return myXmlFormattingPolicy.insertLineBreakBeforeTag(getTag());
  }

  public boolean removeLineBreakBeforeTag() {
    return myXmlFormattingPolicy.removeLineBreakBeforeTag(getTag());
  }

  public boolean isTextElement() {
    return myXmlFormattingPolicy.isTextElement(getTag());
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (myXmlFormattingPolicy.indentChildrenOf(getTag())) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    } else {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }
  }
}
