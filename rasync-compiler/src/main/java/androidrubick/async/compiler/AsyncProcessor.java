package androidrubick.async.compiler;

import com.google.auto.service.AutoService;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import androidrubick.async.annotations.Async;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.getPackage;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.ElementKind.PACKAGE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

// 注意点：
// 1、Async注解只支持接口类型
// 2、Async注解的可见性必须大于等于包可见；
// 3、Async注解的接口的所有方法没有返回值；

@AutoService(Processor.class)
public class AsyncProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Filer filer;

    private final Map<Element, AsyncParser> asyncParserMap = new HashMap<>();
    private final Multimap<Element, AsyncMethod> methodsMap = HashMultimap.create();
    private final Multimap<Element, TypeElement> superInterfaceMap = HashMultimap.create();
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        for (Class<? extends Annotation> annotation : getSupportedAnnotations()) {
            types.add(annotation.getCanonicalName());
        }
        return types;
    }

    private Set<Class<? extends Annotation>> getSupportedAnnotations() {
        Set<Class<? extends Annotation>> annotations = new LinkedHashSet<>();
        annotations.add(Async.class);
        return annotations;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return super.getSupportedOptions();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);

        elementUtils = env.getElementUtils();
        typeUtils = env.getTypeUtils();
        filer = env.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            return processImpl(annotations, roundEnv);
        } catch (Exception e) {
            // We don't allow exceptions of any kind to propagate to the compiler
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            fatalError(writer.toString());
            return true;
        }
    }

    private boolean processImpl(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            generateConfigFiles();
        } else {
            processAnnotations(annotations, roundEnv);
        }
        return true;
    }

    private void processAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        log(annotations.toString());
        parseAsyncAnnotations(annotations, roundEnv);
    }

    private void generateConfigFiles() {
        for (Map.Entry<Element, AsyncParser> entry: asyncParserMap.entrySet()) {
            Element element = entry.getKey();
            JavaFile javaFile = entry.getValue().brewJava();
            try {
                javaFile.writeTo(filer);
            } catch (IOException e) {
                error(element, "Unable to write async for type %s: %s", element, e.getMessage());
            }
        }
    }

    private void parseAsyncAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Async.class);
        log(elements.toString());

        for (Element element : elements) {
            try {
                TypeElement asyncTypeElement = (TypeElement) element;

                // 暂时没有参数之类的设置，无需使用
                AnnotationMirror asyncAnnotation = getAnnotationMirror(element, Async.class).get();

                TypeMirror elementType = element.asType();
                if (isInaccessibleViaGeneratedCode(Async.class, "", element) || isInWrongPackage(Async.class, element)) {
                    continue;
                }

                // 对符合条件的接口，生成`class.package.ClassName_Async`实现类
                resolveSuperAndMethods(element, methodsMap.get(element));

                TypeName srcTypeName = TypeName.get(elementType);

                String packageName = getPackage(asyncTypeElement).getQualifiedName().toString();
                String className = asyncTypeElement.getQualifiedName().toString().substring(
                        packageName.length() + 1).replace('.', '$');
                ClassName asyncClassName = ClassName.get(packageName, className + "_Async");
                TypeName asyncTypeName = asyncClassName;
                if (srcTypeName instanceof ParameterizedTypeName) {
                    ParameterizedTypeName psrcTypeName = ((ParameterizedTypeName) srcTypeName);
                    asyncTypeName = ParameterizedTypeName.get(asyncClassName,
                            psrcTypeName.typeArguments.toArray(new TypeName[psrcTypeName.typeArguments.size()]));
                }

                Set<TypeName> superInterfaces = ImmutableSet.of(srcTypeName);
                Set<AsyncMethod> methods = ImmutableSet.copyOf(methodsMap.get(element));

                asyncParserMap.put(element, new AsyncParser(srcTypeName, asyncTypeName, asyncClassName, superInterfaces, methods));
            } catch (Exception e) {
                logParsingError(element, Async.class, e);
            }
        }
    }

    private void resolveSuperAndMethods(Element element,
                                        Collection<AsyncMethod> methods) {
        if (element.getKind() != INTERFACE) {
            return;
        }
        TypeElement asyncTypeElement = (TypeElement) element;
        List<? extends TypeMirror> superInterfaces = asyncTypeElement.getInterfaces();
        if (!superInterfaces.isEmpty()) {
            for (TypeMirror superT: superInterfaces) {
                resolveSuperAndMethods0(AsyncProcessorHelper.typeMirrorAsElement(superT), methods);
                resolveSuperAndMethods0(superT, methods);
            }
        }
        resolveSuperAndMethods0(element, methods);
    }

    private void resolveSuperAndMethods0(Element element, Collection<AsyncMethod> methods) {
        List<? extends Element> enclosedElements = element.getEnclosedElements();
        for (Element me : enclosedElements) {
            if (me.getKind() == METHOD && me.getModifiers().contains(ABSTRACT)) {
                ExecutableElement executableElement = (ExecutableElement) me;
                TypeMirror returnType = executableElement.getReturnType();
                if (returnType.getKind() != TypeKind.VOID) {
                    error(element, "Unsupport method: %s.%s, for Async only support void methods",
                            element, executableElement);
                    continue;
                }

                String methodName = executableElement.getSimpleName().toString();
                List<? extends VariableElement> variableElements = executableElement.getParameters();
                List<? extends TypeParameterElement> typeParameterElements = executableElement.getTypeParameters();
                // we only care about method
//                methods.add((ExecutableElement) me);

                List<TypeName> parameters = new ArrayList<>();
                if (null != variableElements && !variableElements.isEmpty()) {
                    for (VariableElement ve : variableElements) {
                        parameters.add(TypeName.get(ve.asType()));
                    }
                }
                List<TypeVariableName> typeVariableNames = new ArrayList<>();
                if (null != typeParameterElements && !typeParameterElements.isEmpty()) {
                    for (TypeParameterElement tpe : typeParameterElements) {
                        typeVariableNames.add(TypeVariableName.get(tpe));
                    }
                }
                TypeName returnTypeName = TypeName.get(returnType);
                methods.add(new AsyncMethod(methodName, parameters, returnTypeName, typeVariableNames, null));
            }
        }
    }

    private void resolveSuperAndMethods0(TypeMirror typeMirror, final Collection<AsyncMethod> methods) {
        fatalError("resolveSuperAndMethods0 typeMirror: " + typeMirror);
        DeclaredType dt = (DeclaredType) typeMirror;
        fatalError("resolveSuperAndMethods0 typeMirror: " + dt.getTypeArguments());

        List<? extends Element> enclosedElements = ((DeclaredType) typeMirror).asElement().getEnclosedElements();
        for (Element me : enclosedElements) {
            if (me.getKind() == METHOD && me.getModifiers().contains(ABSTRACT)) {
                ExecutableType executableType = (ExecutableType) typeUtils.asMemberOf(dt, me);
                fatalError("resolveSuperAndMethods0 asMemberOf: " + executableType);
            }
        }
    }

    // 判断所在的包、外部类是否符合条件
    private boolean isInaccessibleViaGeneratedCode(Class<? extends Annotation> annotationClass,
                                                   String targetThing, Element element) {
        boolean hasError = false;
        QualifiedNameable enclosingElement = (QualifiedNameable) element.getEnclosingElement();

        // Verify non-interface type.
        if (element.getKind() != INTERFACE) {
            error(element, "@%s %s may only be interfaces. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            hasError = true;
        }

        // Verify non-private modifiers.
        Set<Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(PRIVATE)) {
            error(element, "@%s %s must not be private. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing,
                    enclosingElement.getQualifiedName(), element.getSimpleName());
            hasError = true;
        }

        // Verify non-package enclosing elements.
        while (enclosingElement.getKind() != PACKAGE) {
            Set<Modifier> enclosingModifiers = enclosingElement.getModifiers();
            // Verify containing class visibility is not private.
            if (enclosingModifiers.contains(PRIVATE)) {
                error(enclosingElement, "@%s %s may not be contained in private classes / interfaces. (%s.%s)",
                        annotationClass.getSimpleName(), targetThing,
                        enclosingElement.getQualifiedName(), element.getSimpleName());
                hasError = true;
            }
            if (enclosingElement.getKind() == CLASS) {
                if (!enclosingModifiers.contains(STATIC) &&
                        enclosingElement.getEnclosingElement().getKind() != PACKAGE) {
                    error(enclosingElement, "@%s %s may not be contained in non-static inner classes. (%s.%s)",
                            annotationClass.getSimpleName(), targetThing,
                            enclosingElement.getQualifiedName(), element.getSimpleName());
                    hasError = true;
                }
            }
            enclosingElement = (QualifiedNameable) enclosingElement.getEnclosingElement();
        }

        return hasError;
    }

    private boolean isInWrongPackage(Class<? extends Annotation> annotationClass,
                                     Element element) {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        String qualifiedName = enclosingElement.getQualifiedName().toString();

        if (qualifiedName.startsWith("android.")) {
            error(element, "@%s-annotated class incorrectly in Android framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName);
            return true;
        }
        if (qualifiedName.startsWith("java.")) {
            error(element, "@%s-annotated class incorrectly in Java framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName);
            return true;
        }

        return false;
    }

    private void log(String msg) {
        if (processingEnv.getOptions().containsKey("debug")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg);
        }
    }

    private void error(String msg, Element element, AnnotationMirror annotation) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element, annotation);
    }

    private void fatalError(String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "FATAL ERROR: " + msg);
    }

    private void error(Element element, String message, Object... args) {
        printMessage(Diagnostic.Kind.ERROR, element, message, args);
    }

    private void note(Element element, String message, Object... args) {
        printMessage(Diagnostic.Kind.NOTE, element, message, args);
    }

    private void printMessage(Diagnostic.Kind kind, Element element, String message, Object[] args) {
        if (null != args && args.length > 0) {
            message = String.format(message, args);
        }
        processingEnv.getMessager().printMessage(kind, message, element);
    }

    private void logParsingError(Element element, Class<? extends Annotation> annotation,
                                 Exception e) {
        StringWriter stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        error(element, "Unable to parse @%s async.\n\n%s", annotation.getSimpleName(), stackTrace);
    }
}
