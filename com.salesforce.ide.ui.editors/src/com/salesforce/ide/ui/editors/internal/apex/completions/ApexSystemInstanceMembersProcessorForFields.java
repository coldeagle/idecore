/*******************************************************************************
 * Copyright (c) 2015 Salesforce.com, inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Salesforce.com, inc. - initial API and implementation
 ******************************************************************************/
package com.salesforce.ide.ui.editors.internal.apex.completions;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementImageProvider;
import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.texteditor.ITextEditor;

import apex.jorje.semantic.ast.compilation.Compilation;
import apex.jorje.semantic.ast.compilation.UserClass;
import apex.jorje.semantic.ast.compilation.UserTrigger;
import apex.jorje.semantic.ast.member.Field;
import apex.jorje.semantic.ast.visitor.AstVisitor;
import apex.jorje.semantic.ast.visitor.SymbolScope;
import apex.jorje.semantic.compiler.ApexCompiler;
import apex.jorje.semantic.compiler.CompilationInput;
import apex.jorje.semantic.compiler.SourceFile;
import apex.jorje.semantic.symbol.member.variable.FieldInfo;
import apex.jorje.semantic.symbol.resolver.SymbolResolverImpl;
import apex.jorje.semantic.symbol.type.TypeInfo;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.salesforce.ide.apex.internal.core.ApexModelManager;
import com.salesforce.ide.apex.internal.core.tooling.systemcompletions.model.AbstractCompletionProposalDisplayable;
import com.salesforce.ide.ui.editors.internal.apex.completions.ApexSystemInstanceMembersProcessorForFields.VariablesVisitor.FieldInfoWrapper;
import com.salesforce.ide.ui.internal.ForceImages;
import com.salesforce.ide.ui.internal.editor.imagesupport.ApexElementImageDescriptor;

/**
 * <p>
 * Provides system completions for instance members (properties and non-static methods) of variables. This is an initial
 * implementation while we aim for tighter integration with the compiler. Thus, it has the following limitations:
 * <ol>
 * <li>Only works with system types</li>
 * <li>Does not understand chaining, e.g. myVar.anInstanceField.anotherInstanceField</li>
 * </ol>
 * </p>
 * 
 * <p>
 * This works for fields inside a top-level class. <br/>
 * TODO: Traverse nested classes.
 * </p>
 * 
 * @author nchen
 * 
 */
public class ApexSystemInstanceMembersProcessorForFields extends ApexCompletionProcessor implements
        IContentAssistProcessor {
    private final ITextEditor editor;
    private VariablesVisitor visitor;

    public ApexSystemInstanceMembersProcessorForFields(ITextEditor editor) {
        this.editor = editor;
    }

    public Compilation getCompilationUnit(IResource resource) {
        return ApexModelManager.INSTANCE.getCompilation((IFile) resource);
    }

    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
        Collection<AbstractCompletionProposalDisplayable> suggestions = Lists.newArrayList();
        ApexCompletionUtils.CompletionPrefix completionPrefix = null;

        visitVariables(viewer);

        try {
            if (!getUtil().hasInvokedNewOnSameLine(viewer, offset)) {
                final String prefix = getUtil().getPrefix(viewer, offset);
                completionPrefix = getUtil().determineFullyQualifiedNameFromPrefix(prefix);

                if (completionPrefix.shouldSuggestVariableName()) {
                    suggestions =
                            Collections2.filter(visitor.fields.values(),
                                new Predicate<AbstractCompletionProposalDisplayable>() {
                                    @Override
                                    public boolean apply(AbstractCompletionProposalDisplayable proposal) {
                                        return proposal.getReplacementString().startsWith(prefix);
                                    }
                                });
                    return getUtil().createProposal(suggestions, prefix, offset, getFieldImage());
                } else if (completionPrefix.shouldSuggestVariableMember()) {
                    String variableName = completionPrefix.segments.get(0);
                    String memberPrefix = completionPrefix.segments.get(1);

                    FieldInfoWrapper fieldInfoWrapper = (FieldInfoWrapper) visitor.fields.get(variableName);
                    FieldInfo fieldInfo = fieldInfoWrapper.fieldInfo;
                    TypeInfo typeInfo = fieldInfo.getType();

                    Collection<AbstractCompletionProposalDisplayable> accept =
                            typeInfo.accept(new TypeInfoUtil.SystemsInstanceMembersCompletionSuggestor(memberPrefix));
                    return getUtil().createProposal(accept, memberPrefix, offset, getMembersImage());
                }

            }
        } catch (Exception e) {
            logger.warn("Error trying to generate auto-completion for fields", e);
        }

        return new ICompletionProposal[0];
    }

    public Image getFieldImage() {
        int accessorFlags_JVM = Flags.AccDefault;
        int accessorFlags_JDT = JavaElementImageDescriptor.FINAL;

        ImageDescriptor desc = JavaElementImageProvider.getFieldImageDescriptor(false, accessorFlags_JVM);
        ApexElementImageDescriptor decoratedDesc =
                new ApexElementImageDescriptor(desc, accessorFlags_JDT, APEX_ICON_SIZE);
        return ForceImages.get(ForceImages.JDT_FIELD, accessorFlags_JVM, decoratedDesc);
    }

    public Image getMembersImage() {
        int accessorFlags_JVM = Flags.AccPublic;
        int accessorFlags_JDT = 0;

        ImageDescriptor desc = ForceImages.getDesc(ForceImages.APEX_GLOBAL_METHOD);
        ApexElementImageDescriptor decoratedDesc =
                new ApexElementImageDescriptor(desc, accessorFlags_JDT, APEX_ICON_SIZE);
        return ForceImages.get(ForceImages.APEX_GLOBAL_METHOD, accessorFlags_JVM, decoratedDesc);
    }

    protected void visitVariables(ITextViewer viewer) {
        IResource resource = (IResource) editor.getEditorInput().getAdapter(IResource.class);
        SourceFile virtualSourceFile =
                SourceFile.builder().setSource(viewer.getDocument().get())
                        .setNamespace(apex.jorje.semantic.compiler.Namespace.EMPTY).build();
        Compilation compilation = getCompilationUnit(resource);
        ApexCompiler compiler =
                ApexCompiler
                        .builder()
                        .setInput(
                            new CompilationInput(Collections.singleton(virtualSourceFile), EmptySymbolProvider.get(),
                                    null, null)).build();
        SymbolScope scope = new SymbolScope(new SymbolResolverImpl(compiler));
        visitor = new VariablesVisitor();
        compilation.traverse(visitor, scope);
    }

    /**
     * Determines the "type" of the declared variable. This currently operates only on the current file and doesn't
     * traverse to the parents or references.
     * 
     * @author nchen
     * 
     */
    static class VariablesVisitor extends AstVisitor<SymbolScope> {
        static class FieldInfoWrapper extends AbstractCompletionProposalDisplayable {

            FieldInfo fieldInfo;

            public FieldInfoWrapper(FieldInfo fieldInfo) {
                this.fieldInfo = fieldInfo;
            }

            @Override
            public String getReplacementString() {
                return fieldInfo.getName();
            }

            @Override
            public String getDisplayString() {
                return null; // Maybe display type information
            }
        }

        Map<String, AbstractCompletionProposalDisplayable> fields = Maps.newHashMap();

        // To enable completions with details from classes and triggers, we tell the visitor that we want to "visit" the innards of those elements
        @Override
        public boolean visit(UserClass node, SymbolScope scope) {
            return true;
        }

        @Override
        public boolean visit(UserTrigger node, SymbolScope scope) {
            return true;
        }

        // These are the elements for which we collect type info for
        @Override
        public boolean visit(Field node, SymbolScope scope) {
            node.resolve(scope.getSymbols());
            FieldInfo fieldInfo = node.getFieldInfo();
            fields.put(fieldInfo.getName().toLowerCase(), new FieldInfoWrapper(fieldInfo));
            return true;
        }

    }
}
