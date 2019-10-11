/*
 * Copyright (C) 2009-2018 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import lombok.AccessLevel;
import lombok.MateuMDDEntity;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Handles the {@code lombok.MateuMDDEntity} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleMateuMDDEntity extends JavacAnnotationHandler<MateuMDDEntity> {
	private HandleConstructor handleConstructor = new HandleConstructor();
	private HandleGetter handleGetter = new HandleGetter();
	private HandleSetter handleSetter = new HandleSetter();
	private HandleJPAEqualsAndHashCode handleEqualsAndHashCode = new HandleJPAEqualsAndHashCode();
	private HandleToString handleToString = new HandleToString();
	
	@Override public void handle(AnnotationValues<MateuMDDEntity> annotation, JCAnnotation ast, JavacNode annotationNode) {
		//handleFlagUsage(annotationNode, ConfigurationKeys.DATA_FLAG_USAGE, "@Data");
		
		//deleteAnnotationIfNeccessary(annotationNode, MateuMDDEntity.class);
		JavacNode typeNode = annotationNode.up();
		boolean notAClass = !isClass(typeNode);

		//annotationNode.addError("@MateuMDDEntity se aplica aquí.");

		if (notAClass) {
			annotationNode.addError("@MateuMDDEntity is only supported on a class.");
			return;
		}
		
		//String staticConstructorName = annotation.getInstance().staticConstructor();
		
		// TODO move this to the end OR move it to the top in eclipse.
		//handleConstructor.generateRequiredArgsConstructor(typeNode, AccessLevel.PUBLIC, staticConstructorName, SkipIfConstructorExists.YES, annotationNode);
		handleConstructor.generateExtraNoArgsConstructor(typeNode, annotationNode);
		generateEntityAnnotation(typeNode, annotationNode);
		generateVersionField(typeNode, annotationNode);
		generateIdField(typeNode, annotationNode);
		handleGetter.generateGetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true, List.<JCAnnotation>nil());
		handleSetter.generateSetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true, List.<JCAnnotation>nil(), List.<JCAnnotation>nil());
		handleEqualsAndHashCode.generateMethods(typeNode, annotationNode, List.<JCTree.JCAnnotation>nil());
		generateToStringForType(typeNode, annotationNode);
	}

	private void generateToStringForType(JavacNode typeNode, JavacNode annotationNode) {

		if (!MemberExistsResult.EXISTS_BY_USER.equals(methodExists("toString", typeNode, false, 0))) {

			JavacTreeMaker maker = typeNode.getTreeMaker();
			ListBuffer<JCTree.JCStatement> statements = new ListBuffer<JCTree.JCStatement>();

			java.util.List<JavacNode> idFields = HandleJPAEqualsAndHashCode.getIdFields(typeNode, maker);

			if (!MemberExistsResult.NOT_EXISTS.equals(methodExists("getName", typeNode, false, 0))) {
				statements.append(maker.Return(maker.Apply(List.<JCTree.JCExpression>nil(), maker.Select(maker.Ident(typeNode.toName("this")), typeNode.toName("getName")), List.<JCTree.JCExpression>nil())));
			} else if (idFields.size() > 0) {
				JCTree.JCExpression concatenation = maker.Literal("");
				for (int pos = 0; pos < idFields.size(); pos++) {
					if (pos > 0) concatenation = maker.Binary(CTC_PLUS, concatenation, maker.Literal(" "));
					JavacNode field = idFields.get(pos);
					concatenation = maker.Binary(CTC_PLUS, concatenation, maker.Apply(List.<JCTree.JCExpression>nil(), maker.Select(maker.Ident(typeNode.toName("this")), typeNode.toName(JavacHandlerUtil.toGetterName(field))), List.<JCTree.JCExpression>nil()));
				}
				statements.append(maker.Return(concatenation));
			} else {
				final JCTree.JCExpression callClassSimpleName;
				JCTree.JCMethodInvocation getClass = maker.Apply(List.<JCTree.JCExpression>nil(),
						maker.Select(maker.Ident(typeNode.toName("this")), typeNode.toName("getClass")),
						List.<JCTree.JCExpression>nil());
				callClassSimpleName = maker.Apply(List.<JCTree.JCExpression>nil(),
						maker.Select(getClass, typeNode.toName("getSimpleName")),
						List.<JCTree.JCExpression>nil());

				JCTree.JCExpression printExpression = maker.Select(chainDots(typeNode, "io", "mateu", "mdd", "core", "util", "Helper"), typeNode.toName("capitalize"));
				List<JCTree.JCExpression> printArgs = List.from(new JCTree.JCExpression[] {callClassSimpleName});
				printExpression = maker.Apply(List.<JCTree.JCExpression>nil(), printExpression, printArgs);

				statements.append(maker.Return(printExpression));
			}

			JCTree.JCBlock body = maker.Block(0, statements.toList());
			injectMethod(typeNode, maker.MethodDef(maker.Modifiers(toJavacModifier(AccessLevel.PUBLIC)), typeNode.toName("toString"), chainDots(typeNode, "java", "lang", "String"), List.<JCTree.JCTypeParameter>nil(), List.<JCTree.JCVariableDecl>nil(), List.<JCTree.JCExpression>nil(), body, null));

		}

	}

	private void generateEntityAnnotation(JavacNode typeNode, JavacNode source) {
		JCTree.JCClassDecl typeDecl = null;
		if (typeNode.get() instanceof JCTree.JCClassDecl) typeDecl = (JCTree.JCClassDecl) typeNode.get();
		addAnnotation(typeDecl.mods, typeNode, source.get().pos, source.get(), typeNode.getContext(),"javax.persistence.Entity", null);
	}

	private void generateVersionField(JavacNode typeNode, JavacNode source) {

		JavacTreeMaker maker = typeNode.getTreeMaker();

		JCTree.JCModifiers mods = maker.Modifiers(toJavacModifier(AccessLevel.PACKAGE));

		JCTree.JCVariableDecl def;
		JavacNode field = injectField(typeNode, def = maker.VarDef(mods, typeNode.toName("__version"), maker.TypeIdent(CTC_INT), maker.Literal(0)));
		addAnnotation(def.mods, field, source.get().pos, source.get(), typeNode.getContext(),"javax.persistence.Version", null);
		handleGetter.generateGetterForField(field, source.get(), AccessLevel.PROTECTED, false, List.<JCAnnotation>nil());

	}

	private void generateIdField(JavacNode typeNode, JavacNode source) {

		JavacTreeMaker maker = typeNode.getTreeMaker();
		java.util.List<JavacNode> idFields = HandleJPAEqualsAndHashCode.getIdFields(typeNode, maker);

		if (idFields.size() == 0) {
			JCTree.JCModifiers mods = maker.Modifiers(toJavacModifier(AccessLevel.PRIVATE));

			JCTree.JCVariableDecl def;
			JavacNode field = injectField(typeNode, def = maker.VarDef(mods, typeNode.toName("id"), maker.TypeIdent(CTC_LONG), maker.Literal(0)));
			addAnnotation(def.mods, field, source.get().pos, source.get(), typeNode.getContext(),"javax.persistence.Id", null);
			addAnnotation(def.mods, field, source.get().pos, source.get(), typeNode.getContext(),"javax.persistence.GeneratedValue", maker.Assign(maker.Ident(typeNode.toName("strategy")), chainDots(typeNode, "javax", "persistence", "GenerationType", "IDENTITY")));
			handleGetter.generateGetterForField(field, source.get(), AccessLevel.PUBLIC, false, List.<JCAnnotation>nil());
			handleSetter.generateSetterForField(field, source, AccessLevel.PUBLIC, List.<JCAnnotation>nil(), List.<JCAnnotation>nil());
		}

	}

}
