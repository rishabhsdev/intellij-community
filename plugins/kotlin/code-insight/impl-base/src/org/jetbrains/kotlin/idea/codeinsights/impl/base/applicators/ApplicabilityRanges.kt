// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators

import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

object ApplicabilityRanges {

    fun ifKeyword(element: KtIfExpression) =
        ApplicabilityRange.single(element) { it.ifKeyword }

    fun whenKeyword(element: KtWhenExpression) =
        ApplicabilityRange.single(element) { it.whenKeyword }

    fun visibilityModifier(element: KtModifierListOwner) =
        modifier(element, KtTokens.VISIBILITY_MODIFIERS)

    fun modalityModifier(element: KtModifierListOwner) =
        modifier(element, KtTokens.MODALITY_MODIFIERS)

    private fun modifier(
        element: KtModifierListOwner,
        tokens: TokenSet,
    ) = ApplicabilityRange.single(element) { it.modifierList?.getModifier(tokens) }

    fun calleeExpression(element: KtCallExpression) =
        ApplicabilityRange.single(element) { it.calleeExpression }

    fun callExcludingLambdaArgument(element: KtCallElement): List<TextRange> {
        val endElement = element.valueArgumentList
            ?: element.calleeExpression
            ?: return emptyList()

        return listOf(TextRange(0, endElement.endOffset - element.startOffset))
    }

    fun valueArgumentExcludingLambda(element: KtValueArgument): List<TextRange> {
        val expression = element.getArgumentExpression()
            ?: return emptyList()

        val textLength = element.textLength
        return if (expression is KtLambdaExpression) {
            // Use OUTSIDE of curly braces only as applicability ranges for lambda.
            // If we use the text range of the curly brace elements, it will include the inside of the braces.
            // This matches FE 1.0 behavior (see AddNameToArgumentIntention).
            listOf(TextRange(0, 0), TextRange(textLength, textLength))
        } else {
            listOf(TextRange(0, textLength))
        }
    }

    fun declarationWithoutInitializer(element: KtCallableDeclaration): List<TextRange> {
        // The IDE seems to treat the end offset inclusively when checking if the caret is within the range. Hence we do minus one here
        // so that the intention is available from the following highlighted range.
        //   val i = 1
        //   ^^^^^^^^
        val endOffset = (element as? KtDeclarationWithInitializer)
            ?.initializer
            ?.let { it.startOffsetInParent - 1 }
            ?: element.textLength

        return listOf(TextRange(0, endOffset))
    }

    fun declarationName(element: KtNamedDeclaration) =
        ApplicabilityRange.single(element) { it.nameIdentifier }
}