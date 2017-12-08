package androidrubick.async.compiler;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * {@doc}
 * <p>
 * Created by Yin Yong on 2017/11/30.
 *
 * @since 1.0.0
 */
class AsyncParser {

    private static final ClassName LOOPER = ClassName.get("android.os", "Looper");
    private static final ClassName HANDLER = ClassName.get("android.os", "Handler");
    private static final ClassName MESSAGE = ClassName.get("android.os", "Message");
    private static final ClassName HANDLER_CALLBACK = ClassName.get("android.os", "Handler", "Callback");
    private static final ParameterSpec HANDLER_CALLBACK_METHOD_P = ParameterSpec.builder(MESSAGE, "msg").build();
    private static final Supplier<MethodSpec.Builder> HANDLER_CALLBACK_METHOD_F = new Supplier<MethodSpec.Builder>() {

        @Override
        public MethodSpec.Builder get() {
            return MethodSpec.methodBuilder("handleMessage")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC, FINAL)
                    .addParameter(HANDLER_CALLBACK_METHOD_P)
                    .returns(TypeName.BOOLEAN);
        }

    };

    private final TypeName srcTypeName;
    private final TypeName asyncTypeName;
    private final ClassName asyncClassName;
    private final Set<TypeName> superInterfaces;
    private Set<AsyncMethod> methods;

    /**
     * @param srcTypeName     原类型名称
     * @param asyncClassName  要生成的class
     * @param superInterfaces
     */
    public AsyncParser(TypeName srcTypeName,
                       TypeName asyncTypeName,
                       ClassName asyncClassName,
                       Set<TypeName> superInterfaces,
                       Set<AsyncMethod> methods) {
        this.srcTypeName = srcTypeName;
        this.asyncTypeName = asyncTypeName;
        this.asyncClassName = asyncClassName;
        this.superInterfaces = superInterfaces;
        this.methods = methods;
    }

    JavaFile brewJava() {
        return JavaFile.builder(asyncClassName.packageName(), createType())
                .addFileComment("Generated code from AndroidRubick Async. Do not modify!")
                .build();
    }

    private TypeSpec createType() {
        TypeSpec.Builder result = TypeSpec.classBuilder(asyncClassName.simpleName())
                .addModifiers(PUBLIC);

        addTypeVariables(result);
        addSuperinterfaces(result);

        // 如果没有需要继承的方法
        if (null == methods || methods.isEmpty()) {
            return result.build();
        }

        addHandlerCallbackAsSuper(result);
        addFields(result);
        addConstructors(result);

        addMethods(result);

        return result.build();
    }

    private void addTypeVariables(TypeSpec.Builder result) {
        if (asyncTypeName instanceof ParameterizedTypeName) {
            for (TypeName ta : ((ParameterizedTypeName) asyncTypeName).typeArguments) {
                if (ta instanceof TypeVariableName) {
                    result.addTypeVariable((TypeVariableName) ta);
                } else {
                    throw new AssertionError(String.format(
                            "Generic parameter type is %s, which is invalid, may only be %s",
                            ta, "TypeVariableName"));
                }
            }
        }
    }

    private void addSuperinterfaces(TypeSpec.Builder result) {
        if (null == superInterfaces || superInterfaces.isEmpty()) {
            return;
        }
        result.addSuperinterfaces(superInterfaces);
    }

    private void addHandlerCallbackAsSuper(TypeSpec.Builder result) {
        result.addSuperinterface(HANDLER_CALLBACK);
    }

    private void addFields(TypeSpec.Builder result) {
        result.addField(FieldSpec.builder(HANDLER, "mHandler", PRIVATE, FINAL)
                .initializer("new $T($T.getMainLooper(), this)", HANDLER, LOOPER)
                .build());

        result.addField(FieldSpec.builder(srcTypeName, "mBase", PRIVATE, FINAL)
                .build());
    }

    private void addConstructors(TypeSpec.Builder result) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(PUBLIC);
        constructor.addParameter(srcTypeName, "base");
        constructor.addStatement("this.mBase = base");

        result.addMethod(constructor.build());
    }

    private void addMethods(TypeSpec.Builder result) {
        // add public boolean handleMessage(Message msg); method
        MethodSpec.Builder handleMessage = HANDLER_CALLBACK_METHOD_F.get();

        if (null != methods && !methods.isEmpty()) {
            CodeBlock.Builder handleMessageBodyBuilder = CodeBlock.builder();
            handleMessageBodyBuilder.beginControlFlow("switch(msg.what)");

            int mIndex = 0;
            for (AsyncMethod m : methods) {
                // current method
                MethodSpec.Builder curMethodBuilder = MethodSpec.methodBuilder(m.name)
                        .addModifiers(PUBLIC)
                        .returns(m.returnType);

                // 加入方法的类型参数
                if (null != m.typeVariables && !m.typeVariables.isEmpty()) {
                    curMethodBuilder.addTypeVariables(m.typeVariables);
                }

                CodeBlock.Builder curMethodBodyBuilder = CodeBlock.builder();
                curMethodBodyBuilder.addStatement("if (null == this.mBase) return");

                // 在主线程时，直接调用的代码
                CodeBlock.Builder mainLooperOp = CodeBlock.builder();
                // 在子线程时，需要组装数据调用的代码
                CodeBlock.Builder subLooperOp = CodeBlock.builder();

                processMethodBody_NoParam(m, mIndex,curMethodBuilder, mainLooperOp, subLooperOp, handleMessageBodyBuilder);

                curMethodBodyBuilder
                        .beginControlFlow("if ($T.getMainLooper() == $T.myLooper())", LOOPER, LOOPER)
                        .add(mainLooperOp.build())
                        .nextControlFlow("else")
                        .add(subLooperOp.build())
                        .endControlFlow();

                curMethodBuilder.addCode(curMethodBodyBuilder.build());
                result.addMethod(curMethodBuilder.build());

                mIndex++;
            }

            handleMessageBodyBuilder.endControlFlow();
            handleMessage.addCode(handleMessageBodyBuilder.build());
        }

        // handleMessage last statement return true
        handleMessage.addStatement("return true");
        result.addMethod(handleMessage.build());
    }

    private void processMethodBody_NoParam(AsyncMethod m, int mIndex,
                                           MethodSpec.Builder methodBuilder,
                                           CodeBlock.Builder mainLooperBuilder,
                                           CodeBlock.Builder subLooperBuilder,
                                           CodeBlock.Builder handlerMessageBuilder
    ) {
        String directParams = "";
        // 在子线程构建map数据
        CodeBlock.Builder prepareHandlerMessage = null;
        // 释放子线程构建map数据
        CodeBlock.Builder postHandlerMessage = null;
        String handlerMessageParams = "";
        if (null != m.parameterTypes && !m.parameterTypes.isEmpty()) {
            String[] directParamsArr = new String[m.parameterTypes.size()];
            prepareHandlerMessage = CodeBlock.builder();
            prepareHandlerMessage.addStatement("$T data = new $T()", TypeName.get(Map.class), TypeName.get(HashMap.class));
            postHandlerMessage = CodeBlock.builder();
            postHandlerMessage.addStatement("Map data = (Map) msg.obj");
            String[] handlerMessageParamsArr = new String[m.parameterTypes.size()];
            int pIndex = 0;
            for (TypeName ptn : m.parameterTypes) {
                String paramStr = "param" + pIndex;
                methodBuilder.addParameter(ptn, paramStr);
                directParamsArr[pIndex] = paramStr;
                // 子线程时，存储在map中发送到main looper handler
                prepareHandlerMessage.addStatement("data.put(\"$L\", $L)", paramStr, paramStr);

                TypeName paramType = ptn.isPrimitive()? ptn.box() : ptn;
                postHandlerMessage.addStatement("$T $L = ($T) data.get(\"$L\")", paramType, paramStr, paramType, paramStr);

                handlerMessageParamsArr[pIndex] = paramStr;
                pIndex ++;
            }
            directParams = Joiner.on(", ").join(directParamsArr);
            handlerMessageParams = Joiner.on(", ").join(handlerMessageParamsArr);
        }

        // 直接调用
        mainLooperBuilder.addStatement("this.mBase.$N($L)", m.name, directParams);
        subLooperBuilder.addStatement("$T msg = mHandler.obtainMessage($L)", MESSAGE, mIndex);
        if (null != prepareHandlerMessage) {
            subLooperBuilder.add(prepareHandlerMessage.build());
            subLooperBuilder.addStatement("msg.obj = data");
        }
        subLooperBuilder.addStatement("msg.sendToTarget()");

        // 拼装handlerMessage中的调用
        // handlerMessage
        handlerMessageBuilder
                .beginControlFlow("case $L:", mIndex);
        if (null != postHandlerMessage) {
            handlerMessageBuilder.add(postHandlerMessage.build());
        }
        handlerMessageBuilder
                .addStatement("mBase.$N($L)", m.name, handlerMessageParams)
                .addStatement("break")
                .endControlFlow();
    }

}
