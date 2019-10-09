package lombok.javac.handlers;

import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import lombok.JPAEqualsAndHashCode;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.core.configuration.CheckerFrameworkVersion;
import lombok.core.handlers.HandlerUtil;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleJPAEqualsAndHashCode extends JavacAnnotationHandler<JPAEqualsAndHashCode> {
    private static final String RESULT_NAME = "result";

    @Override
    public void handle(AnnotationValues<JPAEqualsAndHashCode> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode) {
        deleteAnnotationIfNeccessary(annotationNode, JPAEqualsAndHashCode.class);
        JPAEqualsAndHashCode ann = annotation.getInstance();
        JavacNode typeNode = annotationNode.up();

        generateMethods(typeNode, annotationNode, List.<JCTree.JCAnnotation>nil());
    }

    public void generateMethods(JavacNode typeNode, JavacNode source, List<JCTree.JCAnnotation> onParam) {

        boolean notAClass = true;
        if (typeNode.get() instanceof JCTree.JCClassDecl) {
            long flags = ((JCTree.JCClassDecl) typeNode.get()).mods.flags;
            notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION | Flags.ENUM)) != 0;
        }

        if (notAClass) {
            source.addError("@JPAEqualsAndHashCode is only supported on a class.");
            return;
        }

        boolean isDirectDescendantOfObject = isDirectDescendantOfObject(typeNode);

        if (typeContainsAnnotation(typeNode.get(), chainDots(typeNode, "javax", "persistence", "Entity"))) {
            source.addError("@JPAEqualsAndHashCode is only supported on a @Entity annotated class.");
            return;
        }

        boolean isFinal = (((JCTree.JCClassDecl) typeNode.get()).mods.flags & Flags.FINAL) != 0;
        boolean needsCanEqual = !isFinal || !isDirectDescendantOfObject;
        MemberExistsResult equalsExists = methodExists("equals", typeNode, 1);
        MemberExistsResult hashCodeExists = methodExists("hashCode", typeNode, 0);
        MemberExistsResult canEqualExists = methodExists("canEqual", typeNode, 1);
        switch (Collections.max(Arrays.asList(equalsExists, hashCodeExists))) {
            case EXISTS_BY_LOMBOK:
                return;
            case EXISTS_BY_USER:
                if (equalsExists == MemberExistsResult.NOT_EXISTS || hashCodeExists == MemberExistsResult.NOT_EXISTS) {
                    // This means equals OR hashCode exists and not both.
                    // Even though we should suppress the message about not generating these, this is such a weird and surprising situation we should ALWAYS generate a warning.
                    // The user code couldn't possibly (barring really weird subclassing shenanigans) be in a shippable state anyway; the implementations of these 2 methods are
                    // all inter-related and should be written by the same entity.
                    String msg = String.format("Not generating %s: One of equals or hashCode exists. " +
                                    "You should either write both of these or none of these (in the latter case, lombok generates them).",
                            equalsExists == MemberExistsResult.NOT_EXISTS ? "equals" : "hashCode");
                    source.addWarning(msg);
                }
                return;
            case NOT_EXISTS:
            default:
                //fallthrough
        }

        JCTree.JCMethodDecl equalsMethod = createEquals(typeNode, needsCanEqual, source.get(), onParam);

        injectMethod(typeNode, equalsMethod);

        if (needsCanEqual && canEqualExists == MemberExistsResult.NOT_EXISTS) {
            JCTree.JCMethodDecl canEqualMethod = createCanEqual(typeNode, source.get(), onParam);
            injectMethod(typeNode, canEqualMethod);
        }

        JCTree.JCMethodDecl hashCodeMethod = createHashCode(typeNode, source.get());
        injectMethod(typeNode, hashCodeMethod);
    }

    public JCTree.JCMethodDecl createHashCode(JavacNode typeNode, JCTree source) {

                /*

            @Override
    public int hashCode() {
        return getClass().hashCode();
    }


         */


        JavacTreeMaker maker = typeNode.getTreeMaker();

        JCTree.JCAnnotation overrideAnnotation = maker.Annotation(genJavaLangTypeRef(typeNode, "Override"), List.<JCTree.JCExpression>nil());
        List<JCTree.JCAnnotation> annsOnMethod = List.of(overrideAnnotation);
        CheckerFrameworkVersion checkerFramework = getCheckerFrameworkVersion(typeNode);
        if (checkerFramework.generateSideEffectFree()) annsOnMethod = annsOnMethod.prepend(maker.Annotation(genTypeRef(typeNode, CheckerFrameworkVersion.NAME__SIDE_EFFECT_FREE), List.<JCTree.JCExpression>nil()));
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC, annsOnMethod);
        JCTree.JCExpression returnType = maker.TypeIdent(CTC_INT);
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<JCTree.JCStatement>();

        Name resultName = typeNode.toName(RESULT_NAME);
        long finalFlag = JavacHandlerUtil.addFinalIfNeeded(0L, typeNode.getContext());

        /* return getClass().hashCode(); */
        {
            final JCTree.JCExpression callHashCodeFromClass;
            JCTree.JCMethodInvocation getClass = maker.Apply(List.<JCTree.JCExpression>nil(),
                    maker.Select(maker.Ident(typeNode.toName("this")), typeNode.toName("getClass")),
                    List.<JCTree.JCExpression>nil());
                callHashCodeFromClass = maker.Apply(List.<JCTree.JCExpression>nil(),
                        maker.Select(getClass, typeNode.toName("hashCode")),
                        List.<JCTree.JCExpression>nil());
            statements.append(maker.Return(callHashCodeFromClass));
        }


        JCTree.JCBlock body = maker.Block(0, statements.toList());

        return maker.MethodDef(mods, typeNode.toName("hashCode"), returnType,
                List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>nil(), List.<JCTree.JCExpression>nil(), body, null);
    }



    public JCTree.JCExpression createTypeReference(JavacNode type, boolean addWildcards) {
        java.util.List<String> list = new ArrayList<String>();
        java.util.List<Integer> genericsCount = addWildcards ? new ArrayList<Integer>() : null;

        list.add(type.getName());
        if (addWildcards) genericsCount.add(((JCTree.JCClassDecl) type.get()).typarams.size());
        boolean staticContext = (((JCTree.JCClassDecl) type.get()).getModifiers().flags & Flags.STATIC) != 0;
        JavacNode tNode = type.up();

        while (tNode != null && tNode.getKind() == AST.Kind.TYPE) {
            list.add(tNode.getName());
            if (addWildcards) genericsCount.add(staticContext ? 0 : ((JCTree.JCClassDecl) tNode.get()).typarams.size());
            if (!staticContext) staticContext = (((JCTree.JCClassDecl) tNode.get()).getModifiers().flags & Flags.STATIC) != 0;
            tNode = tNode.up();
        }
        Collections.reverse(list);
        if (addWildcards) Collections.reverse(genericsCount);

        JavacTreeMaker maker = type.getTreeMaker();

        JCTree.JCExpression chain = maker.Ident(type.toName(list.get(0)));
        if (addWildcards) chain = wildcardify(maker, chain, genericsCount.get(0));

        for (int i = 1; i < list.size(); i++) {
            chain = maker.Select(chain, type.toName(list.get(i)));
            if (addWildcards) chain = wildcardify(maker, chain, genericsCount.get(i));
        }

        return chain;
    }

    private JCTree.JCExpression wildcardify(JavacTreeMaker maker, JCTree.JCExpression expr, int count) {
        if (count == 0) return expr;

        ListBuffer<JCTree.JCExpression> wildcards = new ListBuffer<JCTree.JCExpression>();
        for (int i = 0 ; i < count ; i++) {
            wildcards.append(maker.Wildcard(maker.TypeBoundKind(BoundKind.UNBOUND), null));
        }

        return maker.TypeApply(expr, wildcards.toList());
    }

    public JCTree.JCMethodDecl createEquals(JavacNode typeNode, boolean needsCanEqual, JCTree source, List<JCTree.JCAnnotation> onParam) {

        /*

    @Override
    public boolean equals(Object obj) {
        return this == obj || (id != 0 && obj != null && obj instanceof Agency && id == ((Agency) obj).getId());
    }


         */


        JavacTreeMaker maker = typeNode.getTreeMaker();

        Name oName = typeNode.toName("o");
        Name otherName = typeNode.toName("other");
        Name thisName = typeNode.toName("this");

        JCTree.JCAnnotation overrideAnnotation = maker.Annotation(genJavaLangTypeRef(typeNode, "Override"), List.<JCTree.JCExpression>nil());
        List<JCTree.JCAnnotation> annsOnMethod = List.of(overrideAnnotation);
        CheckerFrameworkVersion checkerFramework = getCheckerFrameworkVersion(typeNode);
        if (checkerFramework.generateSideEffectFree()) {
            annsOnMethod = annsOnMethod.prepend(maker.Annotation(genTypeRef(typeNode, CheckerFrameworkVersion.NAME__SIDE_EFFECT_FREE), List.<JCTree.JCExpression>nil()));
        }
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC, annsOnMethod);
        JCTree.JCExpression objectType = genJavaLangTypeRef(typeNode, "Object");
        JCTree.JCExpression returnType = maker.TypeIdent(CTC_BOOLEAN);

        long finalFlag = JavacHandlerUtil.addFinalIfNeeded(0L, typeNode.getContext());

        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<JCTree.JCStatement>();
        final List<JCTree.JCVariableDecl> params = List.of(maker.VarDef(maker.Modifiers(finalFlag | Flags.PARAMETER, onParam), oName, objectType, null));

        /* if (o == this) return true; */ {
            statements.append(maker.If(maker.Binary(CTC_EQUAL, maker.Ident(oName),
                    maker.Ident(thisName)), returnBool(maker, true), null));
        }

        /* if (!(o instanceof Outer.Inner.MyType)) return false; */ {

            JCTree.JCUnary notInstanceOf = maker.Unary(CTC_NOT, maker.Parens(maker.TypeTest(maker.Ident(oName), createTypeReference(typeNode, false))));
            statements.append(maker.If(notInstanceOf, returnBool(maker, false), null));
        }



        /* Outer.Inner.MyType<?> other = (Outer.Inner.MyType<?>) o; */ {
            if (needsCanEqual) {
                final JCTree.JCExpression selfType1 = createTypeReference(typeNode, true), selfType2 = createTypeReference(typeNode, true);

                statements.append(
                        maker.VarDef(maker.Modifiers(finalFlag), otherName, selfType1, maker.TypeCast(selfType2, maker.Ident(oName))));
            }
        }


        /* if (!other.canEqual((java.lang.Object) this)) return false; */ {
            if (needsCanEqual) {
                List<JCTree.JCExpression> exprNil = List.nil();
                JCTree.JCExpression thisRef = maker.Ident(thisName);
                JCTree.JCExpression castThisRef = maker.TypeCast(genJavaLangTypeRef(typeNode, "Object"), thisRef);
                JCTree.JCExpression equalityCheck = maker.Apply(exprNil,
                        maker.Select(maker.Ident(otherName), typeNode.toName("canEqual")),
                        List.of(castThisRef));
                statements.append(maker.If(maker.Unary(CTC_NOT, equalityCheck), returnBool(maker, false), null));
            }
        }


        java.util.List<JavacNode> idFields = getIdFields(typeNode, maker);

        for (JavacNode memberNode : idFields) {
            //addPrintln(maker, statements, typeNode, memberNode.getName());
            boolean isMethod = memberNode.getKind() == AST.Kind.METHOD;

            JCTree.JCExpression fType = unnotate(getFieldType(memberNode, HandlerUtil.FieldAccess.PREFER_FIELD));
            JCTree.JCExpression thisFieldAccessor = isMethod ? createMethodAccessor(maker, memberNode) : createFieldAccessor(maker, memberNode, HandlerUtil.FieldAccess.PREFER_FIELD);
            JCTree.JCExpression otherFieldAccessor = isMethod ? createMethodAccessor(maker, memberNode, maker.Ident(otherName)) : createFieldAccessor(maker, memberNode, HandlerUtil.FieldAccess.PREFER_FIELD, maker.Ident(otherName));
            if (fType instanceof JCTree.JCPrimitiveTypeTree) {
                switch (((JCTree.JCPrimitiveTypeTree)fType).getPrimitiveTypeKind()) {
                    case FLOAT:
                        /* si id == 0 return false; */
                        statements.append(maker.If(maker.Binary(CTC_EQUAL, thisFieldAccessor, maker.Literal(0.0)), returnBool(maker, false), null));
                        statements.append(generateCompareFloatOrDouble(thisFieldAccessor, otherFieldAccessor, maker, typeNode, false));
                    /* if (Float.compare(this.fieldName, other.fieldName) != 0) return false; */
                        statements.append(generateCompareFloatOrDouble(thisFieldAccessor, otherFieldAccessor, maker, typeNode, false));
                        break;
                    case DOUBLE:
                        /* si id == 0 return false; */
                        statements.append(maker.If(maker.Binary(CTC_EQUAL, thisFieldAccessor, maker.Literal(0.0)), returnBool(maker, false), null));
                        /* if (Double.compare(this.fieldName, other.fieldName) != 0) return false; */
                        statements.append(generateCompareFloatOrDouble(thisFieldAccessor, otherFieldAccessor, maker, typeNode, true));
                        break;
                    case INT:
                    case LONG:
                        /* si id == 0 return false; */
                        statements.append(maker.If(maker.Binary(CTC_EQUAL, thisFieldAccessor, maker.Literal(0)), returnBool(maker, false), null));
                        /* if (Double.compare(this.fieldName, other.fieldName) != 0) return false; */
                        statements.append(generateCompareFloatOrDouble(thisFieldAccessor, otherFieldAccessor, maker, typeNode, true));
                        break;
                    default:
                        /* if (this.fieldName != other.fieldName) return false; */
                        statements.append(maker.If(maker.Binary(CTC_NOT_EQUAL, thisFieldAccessor, otherFieldAccessor), returnBool(maker, false), null));
                        break;
                }
            } else if (fType instanceof JCTree.JCArrayTypeTree) {
                JCTree.JCArrayTypeTree array = (JCTree.JCArrayTypeTree) fType;
                /* if (!java.util.Arrays.deepEquals(this.fieldName, other.fieldName)) return false; //use equals for primitive arrays. */
                boolean multiDim = unnotate(array.elemtype) instanceof JCTree.JCArrayTypeTree;
                boolean primitiveArray = unnotate(array.elemtype) instanceof JCTree.JCPrimitiveTypeTree;
                boolean useDeepEquals = multiDim || !primitiveArray;

                JCTree.JCExpression eqMethod = chainDots(typeNode, "java", "util", "Arrays", useDeepEquals ? "deepEquals" : "equals");
                List<JCTree.JCExpression> args = List.of(thisFieldAccessor, otherFieldAccessor);
                statements.append(maker.If(maker.Unary(CTC_NOT,
                        maker.Apply(List.<JCTree.JCExpression>nil(), eqMethod, args)), returnBool(maker, false), null));
            } else /* objects */ {

                /* si id == null return false; */
                statements.append(maker.If(maker.Binary(CTC_EQUAL, thisFieldAccessor, maker.Literal(CTC_BOT, null)), returnBool(maker, false), null));

                /* final java.lang.Object this$fieldName = this.fieldName; */
                /* final java.lang.Object other$fieldName = other.fieldName; */
                /* if (this$fieldName == null ? other$fieldName != null : !this$fieldName.equals(other$fieldName)) return false; */
                Name thisDollarFieldName = memberNode.toName("this" + (isMethod ? "$$" : "$") + memberNode.getName());
                Name otherDollarFieldName = memberNode.toName("other" + (isMethod ? "$$" : "$") + memberNode.getName());

                statements.append(maker.VarDef(maker.Modifiers(finalFlag), thisDollarFieldName, genJavaLangTypeRef(typeNode, "Object"), thisFieldAccessor));
                statements.append(maker.VarDef(maker.Modifiers(finalFlag), otherDollarFieldName, genJavaLangTypeRef(typeNode, "Object"), otherFieldAccessor));

                //addPrintln(maker, statements, typeNode, maker.Ident(thisDollarFieldName));
                //addPrintln(maker, statements, typeNode, maker.Ident(otherDollarFieldName));

                JCTree.JCExpression thisEqualsNull = maker.Binary(CTC_EQUAL, maker.Ident(thisDollarFieldName), maker.Literal(CTC_BOT, null));
                JCTree.JCExpression otherNotEqualsNull = maker.Binary(CTC_NOT_EQUAL, maker.Ident(otherDollarFieldName), maker.Literal(CTC_BOT, null));
                JCTree.JCExpression thisEqualsThat = maker.Apply(List.<JCTree.JCExpression>nil(),
                        maker.Select(maker.Ident(thisDollarFieldName), typeNode.toName("equals")),
                        List.<JCTree.JCExpression>of(maker.Ident(otherDollarFieldName)));
                JCTree.JCExpression fieldsAreNotEqual = maker.Conditional(thisEqualsNull, otherNotEqualsNull, maker.Unary(CTC_NOT, thisEqualsThat));


                statements.append(maker.If(fieldsAreNotEqual, returnBool(maker, false), null));
            }
        }

        /* return true; */ {
            statements.append(returnBool(maker, true));
        }

        /*
        ListBuffer<JCTree.JCStatement> statements2 = new ListBuffer<JCTree.JCStatement>();
        for (JCTree.JCStatement s : statements.toList()) {
            addPrintln(maker, statements2, typeNode, s.toString());
        }
        for (JCTree.JCStatement s : statements.toList()) {
            statements2.append(s);
        }
        */



        JCTree.JCBlock body = maker.Block(0, statements.toList());
        return recursiveSetGeneratedBy(maker.MethodDef(mods, typeNode.toName("equals"), returnType, List.<JCTree.JCTypeParameter>nil(), params, List.<JCTree.JCExpression>nil(), body, null), source, typeNode.getContext());
    }

    private void addPrintln(JavacTreeMaker maker, ListBuffer<JCTree.JCStatement> statements, JavacNode typeNode, String msg) {
        JCTree.JCExpression printExpression = maker.Ident(typeNode.toName("System"));
        printExpression = maker.Select(printExpression, typeNode.toName("out"));
        printExpression = maker.Select(printExpression, typeNode.toName("println"));
        List<JCTree.JCExpression> printArgs = List.from(new JCTree.JCExpression[] {maker.Literal(msg)});
        printExpression = maker.Apply(List.<JCTree.JCExpression>nil(), printExpression, printArgs);
        JCTree.JCStatement call = maker.Exec(printExpression);
        statements.append(call);
    }

    private void addPrintln(JavacTreeMaker maker, ListBuffer<JCTree.JCStatement> statements, JavacNode typeNode, JCTree.JCExpression msg) {
        JCTree.JCExpression printExpression = maker.Ident(typeNode.toName("System"));
        printExpression = maker.Select(printExpression, typeNode.toName("out"));
        printExpression = maker.Select(printExpression, typeNode.toName("println"));
        List<JCTree.JCExpression> printArgs = List.of(msg);
        printExpression = maker.Apply(List.<JCTree.JCExpression>nil(), printExpression, printArgs);
        JCTree.JCStatement call = maker.Exec(printExpression);
        statements.append(call);
    }

    public static java.util.List<JavacNode> getIdFields(JavacNode typeNode, JavacTreeMaker maker) {
        //JCTree.JCAnnotation idAnnotation = maker.Annotation(chainDots(typeNode, "javax", "persistence", "Id"), List.<JCTree.JCExpression>nil());

        java.util.List<JavacNode> fields = new ArrayList<JavacNode>();
        for (JavacNode potentialField : typeNode.down()) {
            if (potentialField.getKind() != AST.Kind.FIELD) continue;
            if (fieldContainsAnnotation(potentialField.get(), "Id")) fields.add(potentialField);
            //if (fieldContainsAnnotation(potentialField.get(), chainDots(potentialField, "javax", "persistence", "Id"))) fields.add(potentialField);
        }

        return fields;
    }

    protected static boolean fieldContainsAnnotation(JCTree field, String annotationName) {
        if (!(field instanceof JCTree.JCVariableDecl)) return false;
        JCTree.JCVariableDecl f = (JCTree.JCVariableDecl) field;
        if (f.mods.annotations == null) return false;
        for (JCTree.JCAnnotation childAnnotation : f.mods.annotations) {
            if (childAnnotation.getAnnotationType().toString().equals(annotationName)) return true;
        }
        return false;
    }

    protected static boolean fieldContainsAnnotation(JCTree field, JCTree annotationType) {
        if (!(field instanceof JCTree.JCVariableDecl)) return false;
        JCTree.JCVariableDecl f = (JCTree.JCVariableDecl) field;
        if (f.mods.annotations == null) return false;
        for (JCTree.JCAnnotation childAnnotation : f.mods.annotations) {
            if (childAnnotation.getAnnotationType().equals(annotationType.toString())) return true;
        }
        return false;
    }

    public static boolean typeContainsAnnotation(JCTree typeNode, JCTree annotationType) {
        if (!(typeNode instanceof JCTree.JCClassDecl)) return false;
        JCTree.JCModifiers mods = ((JCTree.JCClassDecl) typeNode).mods;

        if (mods.annotations == null) return false;
        for (JCTree.JCAnnotation childAnnotation : mods.annotations) {
            if (childAnnotation.getAnnotationType().equals(annotationType)) return true;
        }
        return false;
    }

    public JCTree.JCMethodDecl createCanEqual(JavacNode typeNode, JCTree source, List<JCTree.JCAnnotation> onParam) {
        /* protected boolean canEqual(final java.lang.Object other) {
         *     return other instanceof Outer.Inner.MyType;
         * }
         */
        JavacTreeMaker maker = typeNode.getTreeMaker();

        List<JCTree.JCAnnotation> annsOnMethod = List.nil();
        CheckerFrameworkVersion checkerFramework = getCheckerFrameworkVersion(typeNode);
        if (checkerFramework.generateSideEffectFree()) {
            annsOnMethod = annsOnMethod.prepend(maker.Annotation(genTypeRef(typeNode, CheckerFrameworkVersion.NAME__SIDE_EFFECT_FREE), List.<JCTree.JCExpression>nil()));
        }
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PROTECTED, annsOnMethod);
        JCTree.JCExpression returnType = maker.TypeIdent(CTC_BOOLEAN);
        Name canEqualName = typeNode.toName("canEqual");
        JCTree.JCExpression objectType = genJavaLangTypeRef(typeNode, "Object");
        Name otherName = typeNode.toName("other");
        long flags = JavacHandlerUtil.addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
        List<JCTree.JCVariableDecl> params = List.of(maker.VarDef(maker.Modifiers(flags, onParam), otherName, objectType, null));

        JCTree.JCBlock body = maker.Block(0, List.<JCTree.JCStatement>of(
                maker.Return(maker.TypeTest(maker.Ident(otherName), createTypeReference(typeNode, false)))));

        return recursiveSetGeneratedBy(maker.MethodDef(mods, canEqualName, returnType, List.<JCTree.JCTypeParameter>nil(), params, List.<JCTree.JCExpression>nil(), body, null), source, typeNode.getContext());
    }

    public JCTree.JCStatement generateCompareFloatOrDouble(JCTree.JCExpression thisDotField, JCTree.JCExpression otherDotField,
                                                           JavacTreeMaker maker, JavacNode node, boolean isDouble) {

        /* if (Float.compare(fieldName, other.fieldName) != 0) return false; */
        JCTree.JCExpression clazz = genJavaLangTypeRef(node, isDouble ? "Double" : "Float");
        List<JCTree.JCExpression> args = List.of(thisDotField, otherDotField);
        JCTree.JCBinary compareCallEquals0 = maker.Binary(CTC_NOT_EQUAL, maker.Apply(
                List.<JCTree.JCExpression>nil(), maker.Select(clazz, node.toName("compare")), args), maker.Literal(0));
        return maker.If(compareCallEquals0, returnBool(maker, false), null);
    }

    public JCTree.JCStatement returnBool(JavacTreeMaker maker, boolean bool) {
        return maker.Return(maker.Literal(CTC_BOOLEAN, bool ? 1 : 0));
    }

    private boolean jcAnnotatedTypeInit;
    private Class<?> jcAnnotatedTypeClass = null;
    private Field jcAnnotatedTypeUnderlyingTypeField = null;

    private JCTree.JCExpression unnotate(JCTree.JCExpression type) {
        if (!isJcAnnotatedType(type)) return type;
        if (jcAnnotatedTypeUnderlyingTypeField == null) return type;
        try {
            return (JCTree.JCExpression) jcAnnotatedTypeUnderlyingTypeField.get(type);
        } catch (Exception ignore) {}
        return type;
    }

    private boolean isJcAnnotatedType(JCTree.JCExpression o) {
        if (o == null) return false;
        if (!jcAnnotatedTypeInit) {
            try {
                jcAnnotatedTypeClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCAnnotatedType", false, o.getClass().getClassLoader());
                jcAnnotatedTypeUnderlyingTypeField = jcAnnotatedTypeClass.getDeclaredField("underlyingType");
            }
            catch (Exception ignore) {}
            jcAnnotatedTypeInit = true;
        }
        return jcAnnotatedTypeClass == o.getClass();
    }

}
