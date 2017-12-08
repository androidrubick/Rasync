package androidrubick.async.compiler;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import static javax.lang.model.element.ElementKind.INTERFACE;

/**
 * {@doc}
 * <p>
 * Created by Yin Yong on 2017/11/30.
 * @since 1.0.0
 */
/*package*/ class AsyncProcessorHelper {

    public static boolean isInterface(Element element) {
        return null != element && element.getKind() == INTERFACE;
    }

    public static boolean isInterface(TypeMirror typeMirror) {
        return isInterface(typeMirrorAsElement(typeMirror));
    }

    public static Element typeMirrorAsElement(TypeMirror typeMirror) {
        return typeMirror instanceof DeclaredType ? ((DeclaredType) typeMirror).asElement() : null;
    }

    public static boolean isSubtypeOfType(TypeMirror typeMirror, String otherType) {
        if (isTypeEqual(typeMirror, otherType)) {
            return true;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) typeMirror;
        // 获取泛型参数
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() > 0) {
            StringBuilder typeString = new StringBuilder(declaredType.asElement().toString());
            typeString.append('<');
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) {
                    typeString.append(',');
                }
                typeString.append('?');
            }
            typeString.append('>');
            if (typeString.toString().equals(otherType)) {
                return true;
            }
        }
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        TypeMirror superType = typeElement.getSuperclass();
        if (isSubtypeOfType(superType, otherType)) {
            return true;
        }
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (isSubtypeOfType(interfaceType, otherType)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTypeEqual(TypeMirror typeMirror, String otherType) {
        return null != typeMirror && otherType.equals(typeMirror.toString());
    }
}
