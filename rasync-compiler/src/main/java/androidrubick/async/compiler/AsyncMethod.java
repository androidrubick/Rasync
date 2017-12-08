package androidrubick.async.compiler;

import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;

import java.util.Arrays;
import java.util.List;

/**
 * {@doc}
 * <p>
 * Created by Yin Yong on 2017/11/30.
 *
 * @since 1.0.0
 */
class AsyncMethod {

    String name;
    List<TypeName> parameterTypes;
    TypeName returnType;
    List<TypeVariableName> typeVariables;
    List<TypeName> throwExceptions;

    public AsyncMethod(String name, List<TypeName> parameterTypes, TypeName returnType,
                       List<TypeVariableName> typeVariables,
                       List<TypeName> throwExceptions
                       ) {
        this.name = name;
        this.parameterTypes = parameterTypes;
        this.typeVariables = typeVariables;
        this.returnType = returnType;
        this.throwExceptions = throwExceptions;
    }

//    @Override
//    public boolean equals(Object o) {
//        if (!(o instanceof AsyncMethod)) {
//            return false;
//        }
//        AsyncMethod one = (AsyncMethod) o;
//        Arrays.equals(one.parameterTypes, parameterTypes);
//        return super.equals(o);
//    }
//
//    @Override
//    public int hashCode() {
//        return super.hashCode();
//    }
}
