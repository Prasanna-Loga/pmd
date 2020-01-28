/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.ast;

import static net.sourceforge.pmd.lang.java.ast.JModifier.ABSTRACT;
import static net.sourceforge.pmd.lang.java.ast.JModifier.DEFAULT;
import static net.sourceforge.pmd.lang.java.ast.JModifier.FINAL;
import static net.sourceforge.pmd.lang.java.ast.JModifier.PRIVATE;
import static net.sourceforge.pmd.lang.java.ast.JModifier.PUBLIC;
import static net.sourceforge.pmd.lang.java.ast.JModifier.STATIC;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * List of modifiers of a declaration.
 *
 * <p>This class keeps track of two modifier sets: the {@linkplain #getExplicitModifiers() explicit}
 * one, which is the modifiers that appeared in the source, and the
 * {@linkplain #getEffectiveModifiers() effective} one, which includes
 * modifiers implicitly given by the context of the node.
 *
 * <pre class="grammar">
 *
 *
 *
 * ModifierList         ::= Modifier*
 *
 * Modifier             ::= "public" | "private"  | "protected"
 *                        | "final"  | "abstract" | "static" | "strictfp"
 *                        | "synchronized" | "native" | "default"
 *                        | "volatile" | "transient"
 *                        | {@linkplain ASTAnnotation Annotation}
 *
 *
 * LocalVarModifierList ::= ( "final" | {@link ASTAnnotation Annotation} )*
 *
 * AnnotationList       ::= {@link ASTAnnotation Annotation}*
 *
 * EmptyModifierList    ::= ()
 *
 * </pre>
 */
public final class ASTModifierList extends AbstractJavaNode {

    /** Might as well share it. */
    static final Set<JModifier> JUST_FINAL = Collections.singleton(FINAL);

    private Set<JModifier> explicitModifiers;
    private Set<JModifier> effectiveModifiers;


    ASTModifierList(int id) {
        super(id);
    }

    @Override
    public Object jjtAccept(JavaParserVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }


    @Override
    public <T> void jjtAccept(SideEffectingVisitor<T> visitor, T data) {
        visitor.visit(this, data);
    }


    void setDeclaredModifiers(Set<JModifier> explicit) {
        this.explicitModifiers = explicit;
    }

    /**
     * Returns the set of modifiers written out in the source explicitly.
     * The returned set is unmodifiable.
     */
    public Set<JModifier> getExplicitModifiers() {
        assert explicitModifiers != null : "Parser should have set the explicit modifiers";
        return Collections.unmodifiableSet(explicitModifiers);
    }

    /**
     * Returns the {@linkplain #getExplicitModifiers() declared modifiers},
     * plus the modifiers that are implicitly bestowed by the context or
     * the type of this declaration. E.g. an interface is implicitly abstract,
     * while an interface field is implicitly static.
     * The returned set is unmodifiable.
     */
    public Set<JModifier> getEffectiveModifiers() {
        assert explicitModifiers != null : "Parser should have set the explicit modifiers";

        if (effectiveModifiers == null) {

            EnumSet<JModifier> mods =
                explicitModifiers.isEmpty()
                ? EnumSet.noneOf(JModifier.class)
                : EnumSet.copyOf(explicitModifiers);

            getOwner().jjtAccept(EffectiveModifierVisitor.INSTANCE, mods);

            this.effectiveModifiers = Collections.unmodifiableSet(mods);

        }

        return effectiveModifiers;
    }

    public JavaNode getOwner() {
        return getParent(); // TODO
    }

    /**
     * Returns true if the effective modifiers contain all of the mentioned
     * modifiers.
     *
     * @param mod1 First mod
     * @param mods Other mods
     */
    public boolean hasAll(JModifier mod1, JModifier... mods) {
        Set<JModifier> actual = getEffectiveModifiers();
        return actual.contains(mod1) && (mods.length == 0 || actual.containsAll(Arrays.asList(mods)));
    }

    /**
     * Returns true if the explicit modifiers contain all of the mentioned
     * modifiers.
     *
     * @param mod1 First mod
     * @param mods Other mods
     */
    public boolean hasAllExplicitly(JModifier mod1, JModifier... mods) {
        Set<JModifier> actual = getExplicitModifiers();
        return actual.contains(mod1) && (mods.length == 0 || actual.containsAll(Arrays.asList(mods)));
    }


    /**
     * Returns true if the effective modifiers contain any of the mentioned
     * modifiers.
     *
     * @param mod1 First mod
     * @param mods Other mods
     */
    public boolean hasAny(JModifier mod1, JModifier... mods) {
        Set<JModifier> actual = getEffectiveModifiers();
        return actual.contains(mod1) || Arrays.stream(mods).anyMatch(actual::contains);
    }


    /**
     * Returns true if the explicit modifiers contain any of the mentioned
     * modifiers.
     *
     * @param mod1 First mod
     * @param mods Other mods
     */
    public boolean hasAnyExplicitly(JModifier mod1, JModifier... mods) {
        Set<JModifier> actual = getExplicitModifiers();
        return actual.contains(mod1) || Arrays.stream(mods).anyMatch(actual::contains);
    }

    /**
     * Populates effective modifiers from the declared ones.
     */
    private static class EffectiveModifierVisitor extends SideEffectingVisitorAdapter<Set<JModifier>> {


        private static final EffectiveModifierVisitor INSTANCE = new EffectiveModifierVisitor();

        // TODO strictfp modifier is also implicitly given to descendants

        @Override
        public void visit(ASTAnyTypeDeclaration node, Set<JModifier> effective) {

            ASTAnyTypeDeclaration enclosing = node.getEnclosingType();
            if (enclosing != null && enclosing.isInterface()) {
                effective.add(PUBLIC);
                effective.add(STATIC);
            }

            if (node.isInterface() || node.isAnnotation()) {
                effective.add(ABSTRACT);
                if (!node.isTopLevel()) {
                    effective.add(STATIC);
                }
            } else if (node instanceof ASTEnumDeclaration && !node.isTopLevel()) {
                effective.add(STATIC);
            }

            if (node instanceof ASTEnumDeclaration
                && node.getEnumConstants().none(ASTEnumConstant::isAnonymousClass)) {
                effective.add(FINAL);
            }
        }


        @Override
        public void visit(ASTFieldDeclaration node, Set<JModifier> effective) {
            if (node.getEnclosingType().isInterface()) {
                effective.add(PUBLIC);
                effective.add(STATIC);
                effective.add(FINAL);
            }
        }

        @Override
        public void visit(ASTLocalVariableDeclaration node, Set<JModifier> effective) {
            // resources are implicitly final
            if (node.getParent() instanceof ASTResource) {
                effective.add(FINAL);
            }
        }


        @Override
        public void visit(ASTEnumConstant node, Set<JModifier> effective) {
            effective.add(PUBLIC);
            effective.add(STATIC);
            effective.add(FINAL);
        }

        @Override
        public void visit(ASTAnonymousClassDeclaration node, Set<JModifier> effective) {
            // TODO add static modifier in static context
        }

        @Override
        public void visit(ASTMethodDeclaration node, Set<JModifier> effective) {

            if (node.getEnclosingType().isInterface()) {

                Set<JModifier> declared = node.getModifiers().explicitModifiers;

                if (!declared.contains(PRIVATE)) {
                    effective.add(PUBLIC);
                }
                if (!declared.contains(DEFAULT) && !declared.contains(STATIC)) {
                    effective.add(ABSTRACT);
                }
            }
        }
    }
}
