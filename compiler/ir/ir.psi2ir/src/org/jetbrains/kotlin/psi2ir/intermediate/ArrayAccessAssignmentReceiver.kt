/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.psi2ir.intermediate

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.inlineStatement
import org.jetbrains.kotlin.ir.expressions.isAssignmentOperatorWithResult
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi2ir.generators.CallGenerator
import org.jetbrains.kotlin.psi2ir.generators.pregenerateValueArgumentsUsing
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.types.KotlinType

class ArrayAccessAssignmentReceiver(
    private val irArray: IrExpression,
    private val ktIndexExpressions: List<KtExpression>,
    private val irIndexExpressions: List<IrExpression>,
    private val indexedGetResolvedCall: ResolvedCall<FunctionDescriptor>?,
    private val indexedSetResolvedCall: ResolvedCall<FunctionDescriptor>?,
    private val indexedGetCall: () -> CallBuilder?,
    private val indexedSetCall: () -> CallBuilder?,
    private val callGenerator: CallGenerator,
    private val startOffset: Int,
    private val endOffset: Int,
    private val origin: IrStatementOrigin
) : AssignmentReceiver {

    private val descriptor =
        indexedGetResolvedCall?.resultingDescriptor
            ?: indexedSetResolvedCall?.resultingDescriptor
            ?: throw AssertionError("Array access should have either indexed-get call or indexed-set call")

    override fun assign(withLValue: (LValue) -> IrExpression): IrExpression {
        val kotlinType: KotlinType =
            indexedGetResolvedCall?.run { resultingDescriptor.returnType!! }
                ?: indexedSetResolvedCall?.run { resultingDescriptor.valueParameters.last().type }
                ?: throw AssertionError("Array access should have either indexed-get call or indexed-set call")

        val hasResult = origin.isAssignmentOperatorWithResult()
        val resultType = if (hasResult) kotlinType else callGenerator.context.builtIns.unitType
        val irResultType = callGenerator.translateType(resultType)
        val irBlock = IrBlockImpl(startOffset, endOffset, irResultType, origin)

        val irArrayValue = callGenerator.scope.createTemporaryVariableInBlock(callGenerator.context, irArray, irBlock, "array")

        val ktExpressionToIrIndexValue = HashMap<KtExpression, IntermediateValue>()

        for ((i, irIndex) in irIndexExpressions.withIndex()) {
            ktExpressionToIrIndexValue[ktIndexExpressions[i]] =
                    callGenerator.scope.createTemporaryVariableInBlock(callGenerator.context, irIndex, irBlock, "index$i")
        }

        val irLValue = LValueWithGetterAndSetterCalls(
            callGenerator,
            descriptor,
            { indexedGetCall()?.fillArrayAndIndexArguments(irArrayValue, indexedGetResolvedCall!!, ktExpressionToIrIndexValue) },
            { indexedSetCall()?.fillArrayAndIndexArguments(irArrayValue, indexedSetResolvedCall!!, ktExpressionToIrIndexValue) },
            callGenerator.translateType(kotlinType),
            startOffset, endOffset, origin
        )
        irBlock.inlineStatement(withLValue(irLValue))

        return irBlock
    }

    override fun assign(value: IrExpression): IrExpression {
        val call = indexedSetCall() ?: throw AssertionError("Array access without indexed-get call")
        val ktExpressionToIrIndexExpression = ktIndexExpressions.zip(irIndexExpressions).toMap()
        call.setExplicitReceiverValue(OnceExpressionValue(irArray))
        callGenerator.statementGenerator.pregenerateValueArgumentsUsing(call, indexedSetResolvedCall!!) {
            ktExpressionToIrIndexExpression[it]
        }
        call.lastArgument = value
        return callGenerator.generateCall(startOffset, endOffset, call, IrStatementOrigin.EQ)
    }

    private fun CallBuilder.fillArrayAndIndexArguments(
        arrayValue: IntermediateValue,
        resolvedCall: ResolvedCall<FunctionDescriptor>,
        ktExpressionToIrIndexValue: Map<KtExpression, IntermediateValue>
    ) = apply {
        setExplicitReceiverValue(arrayValue)

        callGenerator.statementGenerator.pregenerateValueArgumentsUsing(this, resolvedCall) { ktExpression ->
            ktExpressionToIrIndexValue[ktExpression]?.load()
        }
    }
}
